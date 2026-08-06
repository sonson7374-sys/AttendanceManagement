package com.attendance.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class ChangeRoleRequest {
    @NotBlank
    private String role;
}
