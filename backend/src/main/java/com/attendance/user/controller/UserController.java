package com.attendance.user.controller;

import com.attendance.common.dto.ApiResponse;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.user.dto.*;
import com.attendance.user.service.UserBulkImportService;
import com.attendance.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserBulkImportService userBulkImportService;

    // 직원관리 목록도 휴일/휴가 캘린더와 동일하게 역할·조직 계층 기준으로 범위가 좁혀진다
    // (SYSTEM_ADMIN/HR_ADMIN 전체, MANAGER는 본인 조직 산하, EMPLOYEE는 본인만) — 서비스 내부에서 처리.
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listUsers(
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String name,
            Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                userService.listUsers(principal.getId(), organizationId, name, pageable)));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @PathVariable Long userId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUser(userId, principal.getId())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(userService.createUser(request)));
    }

    @GetMapping("/bulk/template")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<byte[]> downloadBulkImportTemplate() {
        byte[] excel = userBulkImportService.generateTemplate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("employee_bulk_template.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(excel);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<BulkUserImportResponse>> bulkImportUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Long companyId,
            @RequestPart("file") MultipartFile file) {
        BulkUserImportResponse response = userBulkImportService.importFromExcel(
                principal.getId(), principal.getUsername(), companyId, file);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{userId}/lock")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> lockUser(@PathVariable Long userId) {
        userService.lockUser(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{userId}/unlock")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> unlockUser(@PathVariable Long userId) {
        userService.unlockUser(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{userId}/resign")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resignUser(
            @PathVariable Long userId, @Valid @RequestBody ResignUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.resignUser(userId, request.getResignDate(), principal.getId(), principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable Long userId, @AuthenticationPrincipal UserPrincipal principal) {
        userService.deleteUser(userId, principal.getId(), principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<PasswordResetResponse>> resetPassword(
            @PathVariable Long userId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                userService.resetPasswordByAdmin(userId, principal.getId(), principal.getUsername())));
    }

    /**
     * HR_ADMIN/SYSTEM_ADMIN 외에도 본인 또는 파트장 이상 레벨이 관리하는 하위 직원에 대해
     * 비밀번호를 직접 지정할 수 있다 — 그 권한·대상 범위 검증은 서비스 내부에서 수행한다.
     */
    @PatchMapping("/{userId}/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> setPassword(
            @PathVariable Long userId,
            @Valid @RequestBody SetUserPasswordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.setPasswordByAdmin(userId, request.getNewPassword(), principal.getId(), principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{userId}/devices")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserDeviceResponse>>> listDevices(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.listDevices(userId)));
    }

    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(
            @PathVariable Long userId, @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.changeRole(userId, request.getRole())));
    }

    /**
     * HR_ADMIN/SYSTEM_ADMIN 외에도 본인 또는 파트장 이상 레벨이 관리하는 하위 직원의 프로필을
     * 수정할 수 있다 — 그 권한·대상 범위 검증(및 권한레벨 변경 차단)은 서비스 내부에서 수행한다.
     */
    @PatchMapping("/{userId}/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @PathVariable Long userId, @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(userId, request, principal.getId())));
    }

    @PostMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changeMyPassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/me/devices")
    public ResponseEntity<ApiResponse<UserDeviceResponse>> registerMyDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RegisterDeviceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.registerDevice(principal.getId(), request)));
    }

    @GetMapping("/me/devices")
    public ResponseEntity<ApiResponse<List<UserDeviceResponse>>> listMyDevices(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(userService.listMyDevices(principal.getId())));
    }

    @DeleteMapping("/me/devices/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> revokeMyDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String deviceId) {
        userService.revokeMyDevice(principal.getId(), deviceId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/{userId}/devices/{deviceId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> revokeDeviceByAdmin(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userId, @PathVariable String deviceId) {
        userService.revokeDeviceByAdmin(principal.getId(), principal.getUsername(), userId, deviceId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
