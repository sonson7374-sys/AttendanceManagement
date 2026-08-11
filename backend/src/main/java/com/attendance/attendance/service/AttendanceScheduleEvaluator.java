package com.attendance.attendance.service;

import com.attendance.common.config.AppConfig;
import com.attendance.holiday.repository.HolidayRepository;
import com.attendance.schedule.domain.WorkSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * 출퇴근 시각과 근무제를 근거로 지각·조퇴 여부를 판정하는 단일 기준 로직.
 * attendance_records.is_late/is_early_leave 컬럼은 근무제 재배정이나 과거 보정 등으로
 * 언제든 낡아질 수 있으므로, 화면에 표시할 값은 저장된 컬럼을 그대로 믿지 않고
 * 이 로직으로 조회 시점에 다시 계산해서 사용해야 한다.
 */
@Component
@RequiredArgsConstructor
public class AttendanceScheduleEvaluator {

    private final HolidayRepository holidayRepository;

    public boolean isLate(Instant checkInAt, LocalDate workDate, WorkSchedule schedule) {
        if (checkInAt == null) {
            return false;
        }
        ZonedDateTime seoulCheckIn = checkInAt.atZone(AppConfig.SEOUL);
        return !holidayRepository.existsByHolidayDate(workDate)
                && seoulCheckIn.toLocalTime()
                        .isAfter(schedule.getWorkStartTime().plusMinutes(schedule.getLateThresholdMinutes()));
    }

    // isLate의 배치 버전. 레코드마다 공휴일 여부를 매번 DB로 확인하는 대신, 호출부가 대상 기간의
    // 공휴일을 한 번에 조회해 둔 Set을 넘겨 받아 메모리에서 확인한다(월별 조회 등에서 사용).
    public boolean isLate(Instant checkInAt, LocalDate workDate, WorkSchedule schedule, Set<LocalDate> holidayDates) {
        if (checkInAt == null) {
            return false;
        }
        ZonedDateTime seoulCheckIn = checkInAt.atZone(AppConfig.SEOUL);
        return !holidayDates.contains(workDate)
                && seoulCheckIn.toLocalTime()
                        .isAfter(schedule.getWorkStartTime().plusMinutes(schedule.getLateThresholdMinutes()));
    }

    public boolean isEarlyLeave(Instant checkOutAt, WorkSchedule schedule) {
        if (checkOutAt == null) {
            return false;
        }
        ZonedDateTime seoulCheckOut = checkOutAt.atZone(AppConfig.SEOUL);
        return seoulCheckOut.toLocalTime()
                .isBefore(schedule.getWorkEndTime().minusMinutes(schedule.getEarlyLeaveThresholdMinutes()));
    }

    /**
     * 실제 출퇴근 구간 중 근무제 스케줄(work_start_time~work_end_time) 밖이었던 시간(조기 출근 + 늦은 퇴근)을 구한다.
     * 잔업시간(overtimeMinutes) 저장값과 "근무스케줄 외 근무시간" 실시간 표시값이 서로 다른 기준으로
     * 계산되어 어긋나지 않도록, 두 곳 모두 이 메서드 하나로 통일해 사용한다.
     */
    public int computeOutsideScheduleMinutes(Instant checkInAt, Instant checkOutAt, LocalDate workDate, WorkSchedule schedule) {
        if (checkInAt == null || checkOutAt == null) {
            return 0;
        }
        Instant scheduleStart = workDate.atTime(schedule.getWorkStartTime()).atZone(AppConfig.SEOUL).toInstant();
        Instant scheduleEnd = workDate.atTime(schedule.getWorkEndTime()).atZone(AppConfig.SEOUL).toInstant();
        long before = Math.max(0, Duration.between(checkInAt, scheduleStart).toMinutes());
        long after = Math.max(0, Duration.between(scheduleEnd, checkOutAt).toMinutes());
        return (int) (before + after);
    }
}
