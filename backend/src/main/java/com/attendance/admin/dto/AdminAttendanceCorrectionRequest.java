package com.attendance.admin.dto;

import com.attendance.attendance.domain.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
public class AdminAttendanceCorrectionRequest {
    private Instant checkInAt;
    private Instant checkOutAt;
    private Long workplaceId;
    private AttendanceStatus status;
    private Integer workMinutes;
    private Integer breakMinutes;
    private Integer overtimeMinutes;

    @NotBlank(message = "보정 사유를 입력해주세요.")
    private String reason;
}
