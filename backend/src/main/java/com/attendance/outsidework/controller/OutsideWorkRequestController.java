package com.attendance.outsidework.controller;

import com.attendance.attendance.dto.ApproveChangeRequestRequest;
import com.attendance.common.dto.ApiResponse;
import com.attendance.outsidework.dto.OutsideWorkRequestResponse;
import com.attendance.outsidework.dto.OutsideWorkRequestSubmitRequest;
import com.attendance.outsidework.service.OutsideWorkRequestService;
import com.attendance.user.domain.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/outside-work-requests")
@RequiredArgsConstructor
public class OutsideWorkRequestController {

    private final OutsideWorkRequestService outsideWorkRequestService;

    @PostMapping
    public ResponseEntity<ApiResponse<OutsideWorkRequestResponse>> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OutsideWorkRequestSubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(outsideWorkRequestService.submit(principal, request)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<OutsideWorkRequestResponse>>> myRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(outsideWorkRequestService.getMyRequests(principal.getId())));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<OutsideWorkRequestResponse>>> pendingRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(outsideWorkRequestService.getPendingRequests(principal.getId())));
    }

    @PatchMapping("/{requestId}")
    public ResponseEntity<ApiResponse<OutsideWorkRequestResponse>> process(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long requestId,
            @Valid @RequestBody ApproveChangeRequestRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(outsideWorkRequestService.process(principal, requestId, request)));
    }
}
