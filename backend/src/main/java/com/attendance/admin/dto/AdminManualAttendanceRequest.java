package com.attendance.admin.dto;

import com.attendance.attendance.domain.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class AdminManualAttendanceRequest {

    @NotNull(message = "대상 직원을 선택해주세요.")
    private Long userId;

    @NotNull(message = "근무일을 입력해주세요.")
    private LocalDate workDate;

    private Long workplaceId;
    private Instant checkInAt;
    private Instant checkOutAt;

    @NotNull(message = "근태 상태를 입력해주세요.")
    private AttendanceStatus status;

    private Integer workMinutes;
    private Integer breakMinutes;
    private Integer overtimeMinutes;

    @NotBlank(message = "등록 사유를 입력해주세요.")
    private String reason;
}
