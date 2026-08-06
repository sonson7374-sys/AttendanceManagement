package com.attendance.schedule.dto;

import com.attendance.schedule.domain.WorkSchedule;

import java.time.LocalTime;

public record WorkScheduleResponse(
        Long id,
        Long companyId,
        String name,
        LocalTime workStartTime,
        LocalTime workEndTime,
        int requiredWorkMinutes,
        int overtimeThresholdMin,
        boolean defaultSchedule,
        boolean active,
        String scheduleType,
        int lateThresholdMinutes,
        int earlyLeaveThresholdMinutes,
        int breakMinutes,
        LocalTime nightShiftStart,
        LocalTime nightShiftEnd,
        int holidayWorkThresholdMinutes
) {
    public static WorkScheduleResponse from(WorkSchedule ws) {
        return new WorkScheduleResponse(
                ws.getId(), ws.getCompanyId(), ws.getName(),
                ws.getWorkStartTime(), ws.getWorkEndTime(),
                ws.getRequiredWorkMinutes(), ws.getOvertimeThresholdMin(),
                ws.isDefaultSchedule(), ws.isActive(),
                ws.getScheduleType(), ws.getLateThresholdMinutes(), ws.getEarlyLeaveThresholdMinutes(),
                ws.getBreakMinutes(), ws.getNightShiftStart(), ws.getNightShiftEnd(),
                ws.getHolidayWorkThresholdMinutes()
        );
    }
}
