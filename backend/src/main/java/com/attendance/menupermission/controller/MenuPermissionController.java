package com.attendance.menupermission.controller;

import com.attendance.common.dto.ApiResponse;
import com.attendance.menupermission.dto.MenuPermissionResponse;
import com.attendance.menupermission.dto.MenuPermissionUpsertRequest;
import com.attendance.menupermission.service.MenuPermissionService;
import com.attendance.user.domain.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menu-permissions")
@RequiredArgsConstructor
public class MenuPermissionController {

    private final MenuPermissionService menuPermissionService;

    /**
     * 로그인한 본인의 메뉴/버튼 설정(예외 항목만)을 조회한다. 인증만 되면 누구나 호출 가능.
     * 로그인 권한(role: EMPLOYEE/MANAGER/HR_ADMIN/SYSTEM_ADMIN, API 인가에 쓰이는 값)이 아니라
     * 권한레벨(level, 그룹코드 LEVEL_ROLL 값 — 사장/부문장/본부장/실장/팀장/파트장/직원 등 조직상 직책)
     * 기준으로 메뉴 표시 여부를 조회한다.
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<MenuPermissionResponse>>> my(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(menuPermissionService.getEffectivePermissions(principal.getLevel())));
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<MenuPermissionResponse>>> byRole(@RequestParam String role) {
        return ResponseEntity.ok(ApiResponse.ok(menuPermissionService.getEffectivePermissions(role)));
    }

    @PutMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<MenuPermissionResponse>> upsert(
            @Valid @RequestBody MenuPermissionUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(menuPermissionService.upsert(request)));
    }
}
