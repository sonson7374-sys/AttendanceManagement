package com.attendance.schedule.dto;

import jakarta.validation.constraints.*;

import java.time.LocalTime;

public record WorkScheduleRequest(
        @NotBlank String name,
        @NotNull LocalTime workStartTime,
        @NotNull LocalTime workEndTime,
        @Min(60) @Max(720) int requiredWorkMinutes,
        @Min(60) @Max(720) int overtimeThresholdMin,
        boolean defaultSchedule,
        @NotBlank String scheduleType,
        @Min(0) @Max(120) int lateThresholdMinutes,
        @Min(0) @Max(120) int earlyLeaveThresholdMinutes,
        @Min(0) @Max(480) int breakMinutes,
        LocalTime nightShiftStart,
        LocalTime nightShiftEnd,
        @Min(0) @Max(720) int holidayWorkThresholdMinutes
) {}
