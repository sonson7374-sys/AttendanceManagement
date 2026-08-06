package com.attendance.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PasswordResetResponse {
    private Long userId;
    private String temporaryPassword;
}
