package com.attendance.leave.controller;

import com.attendance.attendance.dto.ApproveChangeRequestRequest;
import com.attendance.common.dto.ApiResponse;
import com.attendance.leave.dto.BulkLeaveImportResponse;
import com.attendance.leave.dto.LeaveRequestResponse;
import com.attendance.leave.dto.LeaveRequestSubmitRequest;
import com.attendance.leave.service.LeaveRequestBulkImportService;
import com.attendance.leave.service.LeaveRequestService;
import com.attendance.user.domain.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final LeaveRequestBulkImportService leaveRequestBulkImportService;

    @PostMapping
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LeaveRequestSubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(leaveRequestService.submit(principal, request)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> myRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(leaveRequestService.getMyRequests(principal.getId())));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> pendingRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(leaveRequestService.getPendingRequests(principal.getId())));
    }

    @GetMapping("/bulk/template")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<byte[]> downloadBulkImportTemplate() {
        byte[] excel = leaveRequestBulkImportService.generateTemplate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("leave_bulk_template.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(excel);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<BulkLeaveImportResponse>> bulkImport(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file) {
        BulkLeaveImportResponse response = leaveRequestBulkImportService.importFromExcel(
                principal.getId(), principal.getUsername(), file);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{requestId}")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> process(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long requestId,
            @Valid @RequestBody ApproveChangeRequestRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(leaveRequestService.process(principal, requestId, request)));
    }
}
