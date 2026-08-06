package com.attendance.attendance.service;

import com.attendance.attendance.domain.AttendanceRecord;
import com.attendance.attendance.dto.AttendanceHistoryResponse;
import com.attendance.attendance.dto.AttendanceRegisterRowResponse;
import com.attendance.attendance.repository.AttendanceRecordRepository;
import com.attendance.common.config.AppConfig;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.holiday.domain.HolidayType;
import com.attendance.holiday.repository.HolidayRepository;
import com.attendance.schedule.domain.WorkSchedule;
import com.attendance.schedule.service.WorkScheduleService;
import com.attendance.workplace.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceQueryService {

    private static final Map<HolidayType, String> HOLIDAY_TYPE_LABEL = Map.of(
            HolidayType.PUBLIC, "공휴일",
            HolidayType.SUBSTITUTE, "대체공휴일",
            HolidayType.COMPANY, "회사휴일",
            HolidayType.WEEKEND, "주말"
    );

    private final AttendanceRecordRepository recordRepository;
    private final WorkplaceRepository workplaceRepository;
    private final WorkScheduleService workScheduleService;
    private final AttendanceScheduleEvaluator scheduleEvaluator;
    private final HolidayRepository holidayRepository;

    @Transactional(readOnly = true)
    public List<AttendanceHistoryResponse> getHistory(Long userId, LocalDate from, LocalDate to) {
        return recordRepository.findByUserIdAndWorkDateBetween(userId, from, to).stream()
                .map(record -> toResponse(userId, record))
                .toList();
    }

    @Transactional(readOnly = true)
    public AttendanceHistoryResponse getById(Long userId, Long recordId) {
        AttendanceRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.ATTENDANCE_RECORD_NOT_FOUND));
        if (!record.getUserId().equals(userId)) {
            // 본인 근태만 조회 가능 — 타인 레코드는 존재 여부를 노출하지 않고 동일하게 404 처리
            throw new AttendanceException(ErrorCode.ATTENDANCE_RECORD_NOT_FOUND);
        }
        return toResponse(userId, record);
    }

    /**
     * "출근부" 리포트: from~to 전체 날짜(레코드가 없는 날 포함)를 순회하며
     * 휴일구분·근무스케줄시간·근무스케줄 외 근무시간을 채워 넣는다.
     */
    @Transactional(readOnly = true)
    public List<AttendanceRegisterRowResponse> getMyRegister(Long userId, LocalDate from, LocalDate to) {
        Map<LocalDate, AttendanceRecord> recordsByDate = recordRepository.findByUserIdAndWorkDateBetween(userId, from, to)
                .stream()
                .collect(Collectors.toMap(AttendanceRecord::getWorkDate, Function.identity()));

        List<AttendanceRegisterRowResponse> rows = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            rows.add(toRegisterRow(userId, date, recordsByDate.get(date)));
        }
        return rows;
    }

    private AttendanceRegisterRowResponse toRegisterRow(Long userId, LocalDate date, AttendanceRecord record) {
        WorkSchedule schedule = null;
        try {
            schedule = workScheduleService.resolveSchedule(userId, date);
        } catch (AttendanceException e) {
            // 근무제를 특정할 수 없으면 스케줄 관련 필드는 비운다.
        }

        AttendanceRegisterRowResponse.AttendanceRegisterRowResponseBuilder builder = AttendanceRegisterRowResponse.builder()
                .workDate(date)
                .holidayLabel(resolveHolidayLabel(date))
                .scheduleStartTime(schedule != null ? schedule.getWorkStartTime() : null)
                .scheduleEndTime(schedule != null ? schedule.getWorkEndTime() : null);

        if (record == null) {
            return builder.build();
        }

        boolean late = false;
        boolean earlyLeave = false;
        Integer outsideScheduleMinutes = null;
        if (schedule != null) {
            late = scheduleEvaluator.isLate(record.getCheckInAt(), date, schedule);
            earlyLeave = scheduleEvaluator.isEarlyLeave(record.getCheckOutAt(), schedule);
            outsideScheduleMinutes = computeOutsideScheduleMinutes(record.getCheckInAt(), record.getCheckOutAt(), date, schedule);
        }

        return builder
                .checkInAt(record.getCheckInAt())
                .checkOutAt(record.getCheckOutAt())
                .workMinutes(record.getWorkMinutes())
                .breakMinutes(record.getBreakMinutes())
                .overtimeMinutes(record.getOvertimeMinutes())
                .nightMinutes(resolveNightMinutes(userId, record))
                .outsideScheduleMinutes(outsideScheduleMinutes)
                .status(record.getStatus())
                .late(late)
                .earlyLeave(earlyLeave)
                .build();
    }

    private String resolveHolidayLabel(LocalDate date) {
        return holidayRepository.findByHolidayDate(date)
                .map(h -> {
                    // 토/일요일은 holidays 테이블에 WEEKEND 타입으로 미리 등록되어 있는데, 그 타입만으로는
                    // 요일을 구분할 수 없으므로 여기서 날짜의 요일을 직접 확인해 라벨을 정한다.
                    if (h.getHolidayType() == HolidayType.WEEKEND) {
                        return weekendLabel(date);
                    }
                    return HOLIDAY_TYPE_LABEL.getOrDefault(h.getHolidayType(), h.getHolidayType().name());
                })
                .orElseGet(() -> weekendLabel(date));
    }

    private String weekendLabel(LocalDate date) {
        DayOfWeek weekday = date.getDayOfWeek();
        if (weekday == DayOfWeek.SATURDAY) return "주말";
        if (weekday == DayOfWeek.SUNDAY) return "휴일";
        return null;
    }

    /** 실제 출퇴근 구간 중 근무제 스케줄(work_start_time~work_end_time) 밖이었던 시간(조기 출근 + 늦은 퇴근)을 구한다. */
    private int computeOutsideScheduleMinutes(Instant checkInAt, Instant checkOutAt, LocalDate workDate, WorkSchedule schedule) {
        if (checkInAt == null || checkOutAt == null) return 0;
        Instant scheduleStart = workDate.atTime(schedule.getWorkStartTime()).atZone(AppConfig.SEOUL).toInstant();
        Instant scheduleEnd = workDate.atTime(schedule.getWorkEndTime()).atZone(AppConfig.SEOUL).toInstant();
        long before = Math.max(0, Duration.between(checkInAt, scheduleStart).toMinutes());
        long after = Math.max(0, Duration.between(scheduleEnd, checkOutAt).toMinutes());
        return (int) (before + after);
    }

    private AttendanceHistoryResponse toResponse(Long userId, AttendanceRecord record) {
        String workplaceName = record.getWorkplaceId() == null ? null :
                workplaceRepository.findById(record.getWorkplaceId())
                        .map(w -> w.getName()).orElse(null);
        boolean late = false;
        boolean earlyLeave = false;
        try {
            WorkSchedule schedule = workScheduleService.resolveSchedule(userId, record.getWorkDate());
            late = scheduleEvaluator.isLate(record.getCheckInAt(), record.getWorkDate(), schedule);
            earlyLeave = scheduleEvaluator.isEarlyLeave(record.getCheckOutAt(), schedule);
        } catch (AttendanceException e) {
            // 근무제를 특정할 수 없으면 지각·조퇴 여부를 판정하지 않는다.
        }
        return AttendanceHistoryResponse.from(record, workplaceName, resolveNightMinutes(userId, record), late, earlyLeave);
    }

    /**
     * 심야 근무 시간은 저장되지 않고 조회 시점에 계산한다 — 근무제에 야간 시간대
     * (nightShiftStart~nightShiftEnd)가 설정되어 있을 때만 실제 출퇴근 구간과 겹치는
     * 시간을 구한다. 설정이 없거나 근무제를 특정할 수 없는 경우 0으로 처리한다.
     */
    private int resolveNightMinutes(Long userId, AttendanceRecord record) {
        if (record.getCheckInAt() == null || record.getCheckOutAt() == null) {
            return 0;
        }
        try {
            WorkSchedule schedule = workScheduleService.resolveSchedule(userId, record.getWorkDate());
            return computeNightMinutes(record.getCheckInAt(), record.getCheckOutAt(), record.getWorkDate(), schedule);
        } catch (AttendanceException e) {
            return 0;
        }
    }

    private int computeNightMinutes(Instant checkInAt, Instant checkOutAt, LocalDate workDate, WorkSchedule schedule) {
        LocalTime nightStart = schedule.getNightShiftStart();
        LocalTime nightEnd = schedule.getNightShiftEnd();
        if (nightStart == null || nightEnd == null) {
            return 0;
        }

        ZonedDateTime nightStartAt = workDate.atTime(nightStart).atZone(AppConfig.SEOUL);
        ZonedDateTime nightEndAt = workDate.atTime(nightEnd).atZone(AppConfig.SEOUL);
        if (!nightEnd.isAfter(nightStart)) {
            // 자정을 넘기는 야간 시간대 (예: 22:00~06:00)
            nightEndAt = nightEndAt.plusDays(1);
        }

        Instant overlapStart = checkInAt.isAfter(nightStartAt.toInstant()) ? checkInAt : nightStartAt.toInstant();
        Instant overlapEnd = checkOutAt.isBefore(nightEndAt.toInstant()) ? checkOutAt : nightEndAt.toInstant();
        long minutes = Duration.between(overlapStart, overlapEnd).toMinutes();
        return (int) Math.max(0, minutes);
    }
}
