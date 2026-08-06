package com.attendance.calendar.controller;

import com.attendance.calendar.dto.CalendarEventCreateRequest;
import com.attendance.calendar.dto.CalendarEventResponse;
import com.attendance.calendar.dto.CalendarEventUpdateRequest;
import com.attendance.calendar.service.CalendarEventService;
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

// 조회는 인증된 모든 사용자 가능(전체 일정 + 본인 개인 일정). 등록/수정/삭제는 서비스 내부에서
// 권한레벨(SYSADMIN/HRADMIN/PRESIDENT)로 검증한다 — role이 아니라 level 기준이라 @PreAuthorize로 표현할 수 없다.
@RestController
@RequestMapping("/api/v1/calendar-events")
@RequiredArgsConstructor
public class CalendarEventController {

    private final CalendarEventService calendarEventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(calendarEventService.list(principal.getId(), from, to)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CalendarEventResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CalendarEventCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(calendarEventService.create(principal, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CalendarEventUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(calendarEventService.update(principal, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        calendarEventService.delete(principal, id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
