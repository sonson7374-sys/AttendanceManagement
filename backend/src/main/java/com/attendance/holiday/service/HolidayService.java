package com.attendance.holiday.service;

import com.attendance.audit.service.AuditLogService;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.holiday.domain.Holiday;
import com.attendance.holiday.domain.HolidayType;
import com.attendance.holiday.dto.BulkHolidayResult;
import com.attendance.holiday.dto.HolidayPresetResponse;
import com.attendance.holiday.dto.HolidayRequest;
import com.attendance.holiday.dto.HolidayResponse;
import com.attendance.holiday.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HolidayService {

    /**
     * 매년 날짜가 고정된 법정공휴일. 설날·추석·부처님오신날은 음력 기준이라 해마다
     * 날짜가 달라 계산할 수 없으므로 프리셋에 포함하지 않는다 — 관리자가 직접 등록해야 한다.
     */
    private static final List<int[]> FIXED_DATE_HOLIDAYS = List.of(
            new int[]{1, 1}, new int[]{3, 1}, new int[]{5, 5}, new int[]{6, 6},
            new int[]{7, 17}, new int[]{8, 15}, new int[]{10, 3}, new int[]{10, 9}, new int[]{12, 25}
    );
    private static final Map<String, String> FIXED_DATE_HOLIDAY_NAMES = Map.ofEntries(
            Map.entry("1-1", "신정"), Map.entry("3-1", "삼일절"), Map.entry("5-5", "어린이날"),
            Map.entry("6-6", "현충일"), Map.entry("7-17", "제헌절"), Map.entry("8-15", "광복절"),
            Map.entry("10-3", "개천절"), Map.entry("10-9", "한글날"), Map.entry("12-25", "크리스마스")
    );

    private final HolidayRepository holidayRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<HolidayResponse> list() {
        return holidayRepository.findAllByOrderByHolidayDateAsc().stream()
                .map(HolidayResponse::from)
                .toList();
    }

    @Transactional
    public HolidayResponse create(HolidayRequest request, Long actorId, String actorEmail) {
        if (holidayRepository.existsByHolidayDate(request.getHolidayDate())) {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "이미 등록된 날짜입니다.");
        }
        Holiday holiday = holidayRepository.save(Holiday.builder()
                .holidayDate(request.getHolidayDate())
                .name(request.getName())
                .holidayType(request.getHolidayType())
                .build());
        auditLogService.record(actorId, actorEmail, "HOLIDAY_CREATED",
                "HOLIDAY", holiday.getId(),
                Map.of("date", holiday.getHolidayDate().toString(), "name", holiday.getName()));
        return HolidayResponse.from(holiday);
    }

    @Transactional
    public HolidayResponse update(Long id, HolidayRequest request, Long actorId, String actorEmail) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!holiday.getHolidayDate().equals(request.getHolidayDate())
                && holidayRepository.existsByHolidayDate(request.getHolidayDate())) {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "이미 등록된 날짜입니다.");
        }
        holiday.update(request.getName(), request.getHolidayType());
        auditLogService.record(actorId, actorEmail, "HOLIDAY_UPDATED",
                "HOLIDAY", holiday.getId(),
                Map.of("date", holiday.getHolidayDate().toString(), "name", holiday.getName()));
        return HolidayResponse.from(holiday);
    }

    @Transactional
    public void delete(Long id, Long actorId, String actorEmail) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND));
        holidayRepository.delete(holiday);
        auditLogService.record(actorId, actorEmail, "HOLIDAY_DELETED",
                "HOLIDAY", id, Map.of("date", holiday.getHolidayDate().toString(), "name", holiday.getName()));
    }

    @Transactional(readOnly = true)
    public List<HolidayPresetResponse> presets(int year) {
        return FIXED_DATE_HOLIDAYS.stream()
                .map(md -> HolidayPresetResponse.builder()
                        .holidayDate(LocalDate.of(year, md[0], md[1]))
                        .name(FIXED_DATE_HOLIDAY_NAMES.get(md[0] + "-" + md[1]))
                        .build())
                .toList();
    }

    @Transactional
    public BulkHolidayResult bulkCreate(List<HolidayRequest> requests, Long actorId, String actorEmail) {
        int created = 0;
        int skipped = 0;
        for (HolidayRequest request : requests) {
            if (holidayRepository.existsByHolidayDate(request.getHolidayDate())) {
                skipped++;
                continue;
            }
            Holiday holiday = holidayRepository.save(Holiday.builder()
                    .holidayDate(request.getHolidayDate())
                    .name(request.getName())
                    .holidayType(request.getHolidayType() != null ? request.getHolidayType() : HolidayType.PUBLIC)
                    .build());
            auditLogService.record(actorId, actorEmail, "HOLIDAY_CREATED",
                    "HOLIDAY", holiday.getId(),
                    Map.of("date", holiday.getHolidayDate().toString(), "name", holiday.getName()));
            created++;
        }
        return BulkHolidayResult.builder().created(created).skipped(skipped).build();
    }
}
