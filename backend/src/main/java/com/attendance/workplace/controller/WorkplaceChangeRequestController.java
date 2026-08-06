package com.attendance.workplace.controller;

import com.attendance.attendance.dto.ApproveChangeRequestRequest;
import com.attendance.common.dto.ApiResponse;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.workplace.dto.WorkplaceChangeRequestResponse;
import com.attendance.workplace.dto.WorkplaceChangeRequestSubmitRequest;
import com.attendance.workplace.service.WorkplaceChangeRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workplace-change-requests")
@RequiredArgsConstructor
public class WorkplaceChangeRequestController {

    private final WorkplaceChangeRequestService workplaceChangeRequestService;

    @PostMapping
    public ResponseEntity<ApiResponse<WorkplaceChangeRequestResponse>> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody WorkplaceChangeRequestSubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workplaceChangeRequestService.submit(principal, request)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<WorkplaceChangeRequestResponse>>> myRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(workplaceChangeRequestService.getMyRequests(principal.getId())));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<WorkplaceChangeRequestResponse>>> pendingRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(workplaceChangeRequestService.getPendingRequests(principal.getId())));
    }

    @PatchMapping("/{requestId}")
    public ResponseEntity<ApiResponse<WorkplaceChangeRequestResponse>> process(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long requestId,
            @Valid @RequestBody ApproveChangeRequestRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workplaceChangeRequestService.process(principal, requestId, request)));
    }
}
