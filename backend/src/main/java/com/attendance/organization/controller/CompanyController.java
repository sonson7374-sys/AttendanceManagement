package com.attendance.organization.controller;

import com.attendance.common.dto.ApiResponse;
import com.attendance.organization.dto.CompanyCreateRequest;
import com.attendance.organization.dto.CompanyCreateResponse;
import com.attendance.organization.dto.CompanyRequest;
import com.attendance.organization.dto.CompanyResponse;
import com.attendance.organization.service.CompanyService;
import com.attendance.user.domain.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 회사 목록 조회는 의도적으로 액터의 소속 회사에 스코핑하지 않는다 — 새 회사를 만들려면
// 이미 어떤 회사들이 있는지 봐야 하기 때문이다(회사 경계 격리 원칙의 유일한 예외).
// 그 외 조직·근무지·근무제·사용자 등 실제 업무 데이터는 여전히 완전히 격리되어 있다.
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(companyService.listCompanies()));
    }

    /** 로그인한 사용자 본인의 소속 회사 정보. 사이드바에 회사명을 표시하는 등 전 직원이 조회할 수 있어야 한다. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CompanyResponse>> getMyCompany(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(companyService.getMyCompany(principal.getCompanyId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyCreateResponse>> create(
            @Valid @RequestBody CompanyCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(201).body(ApiResponse.created(companyService.createCompany(request, principal)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CompanyRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(companyService.updateCompany(id, request, principal)));
    }
}
