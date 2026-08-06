package com.attendance.holiday.controller;

import com.attendance.common.dto.ApiResponse;
import com.attendance.holiday.dto.BulkHolidayResult;
import com.attendance.holiday.dto.HolidayPresetResponse;
import com.attendance.holiday.dto.HolidayRequest;
import com.attendance.holiday.dto.HolidayResponse;
import com.attendance.holiday.service.HolidayService;
import com.attendance.user.domain.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 목록/프리셋 조회는 인증만 되면 누구나 가능(휴일 캘린더는 민감 정보가 아니며, 휴일/휴가
// 관리 화면을 EMPLOYEE·MANAGER 역할도 볼 수 있어야 한다). 등록·수정·삭제는 관리자 전용으로 유지.
@RestController
@RequestMapping("/api/v1/admin/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<HolidayResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(holidayService.list()));
    }

    @GetMapping("/presets/{year}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<HolidayPresetResponse>>> presets(@PathVariable int year) {
        return ResponseEntity.ok(ApiResponse.ok(holidayService.presets(year)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<HolidayResponse>> create(
            @Valid @RequestBody HolidayRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        HolidayResponse response = holidayService.create(request, principal.getId(), principal.getUsername());
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<BulkHolidayResult>> bulkCreate(
            @Valid @RequestBody List<HolidayRequest> requests,
            @AuthenticationPrincipal UserPrincipal principal) {
        BulkHolidayResult result = holidayService.bulkCreate(requests, principal.getId(), principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<HolidayResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody HolidayRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        HolidayResponse response = holidayService.update(id, request, principal.getId(), principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        holidayService.delete(id, principal.getId(), principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
