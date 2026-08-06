package com.attendance.schedule.controller;

import com.attendance.attendance.dto.ApproveChangeRequestRequest;
import com.attendance.common.dto.ApiResponse;
import com.attendance.schedule.dto.WorkScheduleChangeRequestResponse;
import com.attendance.schedule.dto.WorkScheduleChangeRequestSubmitRequest;
import com.attendance.schedule.service.WorkScheduleChangeRequestService;
import com.attendance.user.domain.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/work-schedule-change-requests")
@RequiredArgsConstructor
public class WorkScheduleChangeRequestController {

    private final WorkScheduleChangeRequestService workScheduleChangeRequestService;

    @PostMapping
    public ResponseEntity<ApiResponse<WorkScheduleChangeRequestResponse>> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody WorkScheduleChangeRequestSubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workScheduleChangeRequestService.submit(principal, request)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<WorkScheduleChangeRequestResponse>>> myRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(workScheduleChangeRequestService.getMyRequests(principal.getId())));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<WorkScheduleChangeRequestResponse>>> pendingRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(workScheduleChangeRequestService.getPendingRequests(principal.getId())));
    }

    @PatchMapping("/{requestId}")
    public ResponseEntity<ApiResponse<WorkScheduleChangeRequestResponse>> process(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long requestId,
            @Valid @RequestBody ApproveChangeRequestRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workScheduleChangeRequestService.process(principal, requestId, request)));
    }
}
