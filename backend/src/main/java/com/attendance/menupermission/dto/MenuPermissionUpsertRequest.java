package com.attendance.menupermission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MenuPermissionUpsertRequest {

    @NotBlank
    private String role;

    @NotBlank
    private String menuKey;

    @NotBlank
    private String actionKey;

    private boolean enabled;
}
