package com.attendance.commoncode.controller;

import com.attendance.commoncode.dto.CommonCodeGroupCreateRequest;
import com.attendance.commoncode.dto.CommonCodeGroupResponse;
import com.attendance.commoncode.dto.CommonCodeGroupUpdateRequest;
import com.attendance.commoncode.service.CommonCodeGroupService;
import com.attendance.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/common-code-groups")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class CommonCodeGroupController {

    private final CommonCodeGroupService commonCodeGroupService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CommonCodeGroupResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(commonCodeGroupService.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommonCodeGroupResponse>> create(
            @Valid @RequestBody CommonCodeGroupCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(commonCodeGroupService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CommonCodeGroupResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CommonCodeGroupUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(commonCodeGroupService.update(id, request)));
    }
}
