package com.attendance.attendance.service;

import com.attendance.common.config.AppConfig;
import com.attendance.holiday.repository.HolidayRepository;
import com.attendance.schedule.domain.WorkSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

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

    public boolean isEarlyLeave(Instant checkOutAt, WorkSchedule schedule) {
        if (checkOutAt == null) {
            return false;
        }
        ZonedDateTime seoulCheckOut = checkOutAt.atZone(AppConfig.SEOUL);
        return seoulCheckOut.toLocalTime()
                .isBefore(schedule.getWorkEndTime().minusMinutes(schedule.getEarlyLeaveThresholdMinutes()));
    }
}
