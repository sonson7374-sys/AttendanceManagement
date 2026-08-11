package com.attendance.workplace.controller;

import com.attendance.common.dto.ApiResponse;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.user.dto.UserResponse;
import com.attendance.workplace.dto.BulkAssignRequest;
import com.attendance.workplace.dto.WorkplaceRequest;
import com.attendance.workplace.dto.WorkplaceResponse;
import com.attendance.workplace.service.WorkplaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/workplaces")
@RequiredArgsConstructor
public class WorkplaceController {

    private final WorkplaceService workplaceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<WorkplaceResponse>>> listWorkplaces(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(workplaceService.listByCompany(principal.getCompanyId(), includeInactive)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<WorkplaceResponse>> getWorkplace(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(workplaceService.getWorkplace(id, principal.getCompanyId())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<WorkplaceResponse>> createWorkplace(
            @Valid @RequestBody WorkplaceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(201).body(ApiResponse.created(workplaceService.createWorkplace(request, principal)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<WorkplaceResponse>> updateWorkplace(
            @PathVariable Long id, @Valid @RequestBody WorkplaceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(workplaceService.updateWorkplace(id, request, principal)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateWorkplace(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        workplaceService.deactivateWorkplace(id, principal);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateWorkplace(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        workplaceService.activateWorkplace(id, principal);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> permanentlyDeleteWorkplace(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        workplaceService.permanentlyDeleteWorkplace(id, principal);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{workplaceId}/users/{userId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> assignUser(
            @PathVariable Long workplaceId,
            @PathVariable Long userId,
            @RequestParam(required = false) LocalDate validFrom,
            @RequestParam(required = false) LocalDate validTo,
            @AuthenticationPrincipal UserPrincipal principal) {
        workplaceService.assignUserToWorkplace(userId, workplaceId, validFrom, validTo, principal);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{workplaceId}/users/bulk")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> assignUsersBulk(
            @PathVariable Long workplaceId,
            @Valid @RequestBody BulkAssignRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        workplaceService.assignUsersToWorkplace(workplaceId, request.getUserIds(),
                request.getValidFrom(), request.getValidTo(), principal);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{workplaceId}/users")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAssignedUsers(
            @PathVariable Long workplaceId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(workplaceService.getAssignedUsers(workplaceId, principal.getCompanyId())));
    }

    @DeleteMapping("/{workplaceId}/users/{userId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeUser(
            @PathVariable Long workplaceId, @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        workplaceService.removeUserFromWorkplace(workplaceId, userId, principal);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/assigned")
    public ResponseEntity<ApiResponse<List<WorkplaceResponse>>> getMyWorkplaces(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(
                workplaceService.getAssignedWorkplaces(principal.getId(), targetDate)));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<WorkplaceResponse>>> getWorkplacesForUser(
            @PathVariable Long userId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(workplaceService.getWorkplacesForUser(userId, principal.getCompanyId())));
    }
}
