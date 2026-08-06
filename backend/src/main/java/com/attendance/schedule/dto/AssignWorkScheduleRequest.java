package com.attendance.schedule.dto;

import jakarta.validation.constraints.NotNull;

public record AssignWorkScheduleRequest(
        @NotNull Long workScheduleId
) {
}
