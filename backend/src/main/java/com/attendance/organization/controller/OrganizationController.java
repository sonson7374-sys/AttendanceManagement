package com.attendance.organization.controller;

import com.attendance.audit.service.AuditLogService;
import com.attendance.common.dto.ApiResponse;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.organization.domain.Organization;
import com.attendance.organization.dto.OrganizationRequest;
import com.attendance.organization.dto.OrganizationResponse;
import com.attendance.organization.repository.OrganizationRepository;
import com.attendance.user.domain.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 조회(목록/단건)는 인증만 되면 누구나 가능 — 부서명은 민감 정보가 아니고, 근태조회 등
// 다른 화면의 부서 필터가 EMPLOYEE 역할에서도 동작해야 한다. 등록/수정/삭제는 관리자 전용 유지.
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationRepository organizationRepository;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrganizationResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                organizationRepository.findByCompanyIdAndActive(principal.getCompanyId(), true)
                        .stream().map(OrganizationResponse::from).toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrganizationResponse>> get(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        Organization org = findOwnedOrThrow(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(OrganizationResponse.from(org)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<OrganizationResponse>> create(
            @Valid @RequestBody OrganizationRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        Organization org = Organization.builder()
                .companyId(principal.getCompanyId())
                .parentId(req.getParentId())
                .name(req.getName())
                .displayOrder(req.getDisplayOrder())
                .build();
        org = organizationRepository.save(org);
        auditLogService.record(principal.getId(), principal.getUsername(), "ORGANIZATION_CREATED",
                "ORGANIZATION", org.getId(), Map.of("name", org.getName()));
        return ResponseEntity.status(201).body(ApiResponse.created(OrganizationResponse.from(org)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<OrganizationResponse>> update(
            @PathVariable Long id, @Valid @RequestBody OrganizationRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        Organization org = findOwnedOrThrow(id, principal);
        String beforeName = org.getName();
        org.update(req.getName(), req.getParentId(), req.getDisplayOrder());
        org = organizationRepository.save(org);
        auditLogService.record(principal.getId(), principal.getUsername(), "ORGANIZATION_UPDATED",
                "ORGANIZATION", org.getId(), Map.of("before", beforeName, "after", org.getName()));
        return ResponseEntity.ok(ApiResponse.ok(OrganizationResponse.from(org)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        Organization org = findOwnedOrThrow(id, principal);
        org.deactivate();
        organizationRepository.save(org);
        auditLogService.record(principal.getId(), principal.getUsername(), "ORGANIZATION_DEACTIVATED",
                "ORGANIZATION", id, Map.of());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 다른 회사의 조직 id는 존재 여부 자체를 노출하지 않도록 NOT_FOUND로 처리한다(회사 경계는 서버가 강제).
    private Organization findOwnedOrThrow(Long id, UserPrincipal principal) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new AttendanceException(ErrorCode.ORGANIZATION_NOT_FOUND));
        if (!org.getCompanyId().equals(principal.getCompanyId())) {
            throw new AttendanceException(ErrorCode.ORGANIZATION_NOT_FOUND);
        }
        return org;
    }
}
