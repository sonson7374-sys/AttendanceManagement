package com.attendance.attendance.controller;

import com.attendance.attendance.dto.*;
import com.attendance.attendance.service.AttendanceQueryService;
import com.attendance.attendance.service.AttendanceService;
import com.attendance.attendance.service.ChangeRequestService;
import com.attendance.common.dto.ApiResponse;
import com.attendance.user.domain.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceQueryService attendanceQueryService;
    private final ChangeRequestService changeRequestService;

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkIn(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CheckInRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(ApiResponse.ok(
                attendanceService.checkIn(principal.getId(), request, idempotencyKey)));
    }

    @PostMapping("/check-out")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkOut(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CheckOutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(ApiResponse.ok(
                attendanceService.checkOut(principal.getId(), request, idempotencyKey)));
    }

    @PostMapping("/break-start")
    public ResponseEntity<ApiResponse<AttendanceResponse>> startBreak(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                attendanceService.startBreak(principal.getId())));
    }

    @PostMapping("/break-end")
    public ResponseEntity<ApiResponse<AttendanceResponse>> endBreak(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                attendanceService.endBreak(principal.getId())));
    }

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<TodayAttendanceResponse>> today(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                attendanceService.getTodayAttendance(principal.getId())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AttendanceHistoryResponse>>> history(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(
                attendanceQueryService.getHistory(principal.getId(), from, to)));
    }

    @GetMapping("/register")
    public ResponseEntity<ApiResponse<List<AttendanceRegisterRowResponse>>> register(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(
                attendanceQueryService.getMyRegister(principal.getId(), from, to)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AttendanceHistoryResponse>> getById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                attendanceQueryService.getById(principal.getId(), id)));
    }

    @PostMapping("/change-requests")
    public ResponseEntity<ApiResponse<ChangeRequestResponse>> submitChangeRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangeRequestSubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                changeRequestService.submit(principal, request)));
    }

    @GetMapping("/change-requests/my")
    public ResponseEntity<ApiResponse<List<ChangeRequestResponse>>> myChangeRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                changeRequestService.getMyRequests(principal.getId())));
    }

    @GetMapping("/change-requests/pending")
    public ResponseEntity<ApiResponse<List<ChangeRequestResponse>>> pendingChangeRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                changeRequestService.getPendingRequests(principal.getId())));
    }

    @PatchMapping("/change-requests/{requestId}")
    public ResponseEntity<ApiResponse<ChangeRequestResponse>> processChangeRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long requestId,
            @Valid @RequestBody ApproveChangeRequestRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                changeRequestService.process(principal, requestId, request)));
    }
}
