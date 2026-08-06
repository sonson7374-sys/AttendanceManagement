package com.attendance.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class ResignUserRequest {
    @NotNull(message = "퇴사일을 입력해주세요.")
    private LocalDate resignDate;
}
