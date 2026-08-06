package com.attendance.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SetUserPasswordRequest {
    @NotBlank
    @Size(min = 8)
    private String newPassword;
}
