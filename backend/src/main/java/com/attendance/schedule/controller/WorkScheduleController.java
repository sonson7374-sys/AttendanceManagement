package com.attendance.schedule.controller;

import com.attendance.audit.service.AuditLogService;
import com.attendance.common.dto.ApiResponse;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.schedule.domain.WorkSchedule;
import com.attendance.schedule.dto.AssignWorkScheduleRequest;
import com.attendance.schedule.dto.WorkScheduleRequest;
import com.attendance.schedule.dto.WorkScheduleResponse;
import com.attendance.schedule.repository.WorkScheduleRepository;
import com.attendance.schedule.service.WorkScheduleService;
import com.attendance.user.domain.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/work-schedules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
public class WorkScheduleController {

    private static final long DEFAULT_COMPANY_ID = 1L;

    private final WorkScheduleRepository workScheduleRepository;
    private final WorkScheduleService workScheduleService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkScheduleResponse>>> list() {
        List<WorkScheduleResponse> list = workScheduleRepository
                .findAll().stream()
                .filter(WorkSchedule::isActive)
                .map(WorkScheduleResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> get(@PathVariable Long id) {
        WorkSchedule ws = workScheduleRepository.findById(id)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok(WorkScheduleResponse.from(ws)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> create(
            @Valid @RequestBody WorkScheduleRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (!req.workEndTime().isAfter(req.workStartTime())) {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "근무 종료 시각은 시작 시각보다 늦어야 합니다.");
        }
        WorkSchedule ws = WorkSchedule.builder()
                .companyId(DEFAULT_COMPANY_ID)
                .name(req.name())
                .workStartTime(req.workStartTime())
                .workEndTime(req.workEndTime())
                .requiredWorkMinutes(req.requiredWorkMinutes())
                .overtimeThresholdMin(req.overtimeThresholdMin())
                .defaultSchedule(req.defaultSchedule())
                .scheduleType(req.scheduleType())
                .lateThresholdMinutes(req.lateThresholdMinutes())
                .earlyLeaveThresholdMinutes(req.earlyLeaveThresholdMinutes())
                .breakMinutes(req.breakMinutes())
                .nightShiftStart(req.nightShiftStart())
                .nightShiftEnd(req.nightShiftEnd())
                .holidayWorkThresholdMinutes(req.holidayWorkThresholdMinutes())
                .build();
        ws = workScheduleRepository.save(ws);
        auditLogService.record(principal.getId(), principal.getUsername(), "WORK_SCHEDULE_CREATED",
                "WORK_SCHEDULE", ws.getId(), Map.of("name", ws.getName()));
        return ResponseEntity.status(201).body(ApiResponse.created(WorkScheduleResponse.from(ws)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> update(
            @PathVariable Long id, @Valid @RequestBody WorkScheduleRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        WorkSchedule ws = workScheduleRepository.findById(id)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND));
        ws.update(req.name(), req.workStartTime(), req.workEndTime(),
                req.requiredWorkMinutes(), req.overtimeThresholdMin(),
                req.scheduleType(), req.lateThresholdMinutes(), req.earlyLeaveThresholdMinutes(),
                req.breakMinutes(), req.nightShiftStart(), req.nightShiftEnd(),
                req.holidayWorkThresholdMinutes());
        ws = workScheduleRepository.save(ws);
        auditLogService.record(principal.getId(), principal.getUsername(), "WORK_SCHEDULE_UPDATED",
                "WORK_SCHEDULE", ws.getId(), Map.of("name", ws.getName()));
        return ResponseEntity.ok(ApiResponse.ok(WorkScheduleResponse.from(ws)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        WorkSchedule ws = workScheduleRepository.findById(id)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND));
        ws.deactivate();
        workScheduleRepository.save(ws);
        auditLogService.record(principal.getId(), principal.getUsername(), "WORK_SCHEDULE_DEACTIVATED",
                "WORK_SCHEDULE", id, Map.of());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/users/{userId}/current")
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> getCurrentForUser(@PathVariable Long userId) {
        WorkSchedule ws = workScheduleService.resolveSchedule(userId, LocalDate.now());
        return ResponseEntity.ok(ApiResponse.ok(WorkScheduleResponse.from(ws)));
    }

    /** 로그인한 본인의 근무제 조회. 클래스 레벨 권한(MANAGER 이상)과 별개로 전 직원이 본인 근무제는 조회할 수 있어야 한다. */
    @GetMapping("/assigned")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> getMySchedule(@AuthenticationPrincipal UserPrincipal principal) {
        WorkSchedule ws = workScheduleService.resolveSchedule(principal.getId(), LocalDate.now());
        return ResponseEntity.ok(ApiResponse.ok(WorkScheduleResponse.from(ws)));
    }

    /** 근무제 변경요청 화면에서 선택 가능한 활성 근무제 목록. 전 직원이 조회할 수 있어야 한다(등록/수정 권한과 무관). */
    @GetMapping("/options")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<WorkScheduleResponse>>> listOptions() {
        List<WorkScheduleResponse> list = workScheduleRepository
                .findAll().stream()
                .filter(WorkSchedule::isActive)
                .map(WorkScheduleResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> assignToUser(
            @PathVariable Long userId, @Valid @RequestBody AssignWorkScheduleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        workScheduleService.assignWorkSchedule(userId, request.workScheduleId(), principal);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
