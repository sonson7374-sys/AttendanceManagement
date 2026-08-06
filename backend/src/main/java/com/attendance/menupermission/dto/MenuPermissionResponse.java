package com.attendance.menupermission.dto;

import com.attendance.menupermission.domain.MenuPermission;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MenuPermissionResponse {
    private String role;
    private String menuKey;
    private String actionKey;
    private boolean enabled;

    public static MenuPermissionResponse from(MenuPermission p) {
        return MenuPermissionResponse.builder()
                .role(p.getRole())
                .menuKey(p.getMenuKey())
                .actionKey(p.getActionKey())
                .enabled(p.isEnabled())
                .build();
    }
}
