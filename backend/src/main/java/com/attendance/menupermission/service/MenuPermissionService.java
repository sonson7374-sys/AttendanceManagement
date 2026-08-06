package com.attendance.menupermission.service;

import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.menupermission.domain.MenuPermission;
import com.attendance.menupermission.dto.MenuPermissionResponse;
import com.attendance.menupermission.dto.MenuPermissionUpsertRequest;
import com.attendance.menupermission.repository.MenuPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// menu_permissions.role 컬럼은 이제 로그인 권한(UserRole)이 아니라 권한레벨(그룹코드 LEVEL_ROLL 값,
// 예: SYSADMIN/HRADMIN/PRESIDENT/DIVHEAD/HQHEAD/OFFICEHEAD/TEAMLEAD/PARTLEAD/EMPLOYEE)을 저장한다.
// 컬럼/필드명은 하위 호환을 위해 그대로 두되, 값의 의미가 바뀌었다는 점에 주의한다.
@Service
@RequiredArgsConstructor
public class MenuPermissionService {

    private static final String GUARDED_ROLE = "SYSADMIN";
    private static final String GUARDED_MENU_KEY = "permissions";
    private static final String GUARDED_ACTION_KEY = "MENU";

    private final MenuPermissionRepository menuPermissionRepository;

    @Transactional(readOnly = true)
    public List<MenuPermissionResponse> getEffectivePermissions(String role) {
        return menuPermissionRepository.findByRole(role).stream()
                .map(MenuPermissionResponse::from)
                .toList();
    }

    @Transactional
    public MenuPermissionResponse upsert(MenuPermissionUpsertRequest request) {
        if (GUARDED_ROLE.equals(request.getRole())
                && GUARDED_MENU_KEY.equals(request.getMenuKey())
                && GUARDED_ACTION_KEY.equals(request.getActionKey())
                && !request.isEnabled()) {
            throw new AttendanceException(ErrorCode.INVALID_INPUT,
                    "시스템관리자의 권한관리 메뉴는 숨길 수 없습니다.");
        }

        MenuPermission permission = menuPermissionRepository
                .findByRoleAndMenuKeyAndActionKey(request.getRole(), request.getMenuKey(), request.getActionKey())
                .orElse(null);

        if (permission == null) {
            permission = menuPermissionRepository.save(MenuPermission.builder()
                    .role(request.getRole())
                    .menuKey(request.getMenuKey())
                    .actionKey(request.getActionKey())
                    .enabled(request.isEnabled())
                    .build());
        } else {
            permission.changeEnabled(request.isEnabled());
        }

        return MenuPermissionResponse.from(permission);
    }
}
