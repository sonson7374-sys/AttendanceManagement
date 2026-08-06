package com.attendance.commoncode.controller;

import com.attendance.commoncode.dto.CommonCodeCreateRequest;
import com.attendance.commoncode.dto.CommonCodeResponse;
import com.attendance.commoncode.dto.CommonCodeUpdateRequest;
import com.attendance.commoncode.service.CommonCodeService;
import com.attendance.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 조회(목록)는 인증만 되면 누구나 가능 — 직원 등록/수정 화면의 권한레벨(LEVEL_ROLL) 선택창 등
// 관리자 전용이 아닌 화면에서도 한글명을 불러와야 한다. 등록/수정/삭제는 시스템관리자 전용 유지.
@RestController
@RequestMapping("/api/v1/admin/common-codes")
@RequiredArgsConstructor
public class CommonCodeController {

    private final CommonCodeService commonCodeService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CommonCodeResponse>>> list(@RequestParam String groupCode) {
        return ResponseEntity.ok(ApiResponse.ok(commonCodeService.list(groupCode)));
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<CommonCodeResponse>> create(@Valid @RequestBody CommonCodeCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(commonCodeService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<CommonCodeResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CommonCodeUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(commonCodeService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        commonCodeService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
