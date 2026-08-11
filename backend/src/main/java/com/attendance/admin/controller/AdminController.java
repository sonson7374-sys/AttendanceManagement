package com.attendance.admin.controller;

import com.attendance.admin.dto.AdminAttendanceBoardRow;
import com.attendance.admin.dto.AttendanceScopeInfoResponse;
import com.attendance.admin.dto.AdminAttendanceCorrectionRequest;
import com.attendance.admin.dto.AdminAttendanceResponse;
import com.attendance.admin.dto.AdminCloseMonthRequest;
import com.attendance.admin.dto.AdminManualAttendanceRequest;
import com.attendance.admin.dto.AdminMonthlyUserSummary;
import com.attendance.admin.dto.AuditLogResponse;
import com.attendance.admin.dto.DashboardStatsResponse;
import com.attendance.admin.service.AdminService;
import com.attendance.admin.service.DdlExportService;
import com.attendance.attendance.domain.AttendanceStatus;
import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.attendance.dto.ChangeRequestResponse;
import com.attendance.attendance.service.ChangeRequestService;
import com.attendance.audit.domain.AuditLog;
import com.attendance.audit.repository.AuditLogRepository;
import com.attendance.common.dto.ApiResponse;
import com.attendance.leave.dto.LeaveRequestResponse;
import com.attendance.leave.service.LeaveRequestService;
import com.attendance.organization.service.OrganizationScopeService;
import com.attendance.outsidework.dto.OutsideWorkRequestResponse;
import com.attendance.outsidework.service.OutsideWorkRequestService;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.schedule.dto.WorkScheduleChangeRequestResponse;
import com.attendance.schedule.service.WorkScheduleChangeRequestService;
import com.attendance.workplace.dto.WorkplaceChangeRequestResponse;
import com.attendance.workplace.service.WorkplaceChangeRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'HR_ADMIN', 'SYSTEM_ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final AuditLogRepository auditLogRepository;
    private final ChangeRequestService changeRequestService;
    private final LeaveRequestService leaveRequestService;
    private final OutsideWorkRequestService outsideWorkRequestService;
    private final WorkplaceChangeRequestService workplaceChangeRequestService;
    private final WorkScheduleChangeRequestService workScheduleChangeRequestService;
    private final OrganizationScopeService organizationScopeService;
    private final DdlExportService ddlExportService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> dashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getDashboardStats(principal.getCompanyId())));
    }

    /**
     * 근태조회(일별) 화면. 근태조회(월별)와 동일하게 권한레벨(LEVEL_ROLL) 기준으로 조회 범위를 적용한다
     * (SYSADMIN/HRADMIN/PRESIDENT 전체, 파트장 이상 레벨은 본인 조직 산하 + 본인, 직원 레벨은 본인만) —
     * 클래스 레벨 @PreAuthorize(MANAGER 이상)를 이 메서드에서는 명시적으로 완화한다.
     */
    @GetMapping("/attendance/daily")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<AdminAttendanceResponse>>> dailyAttendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long workplaceId,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String employeeName,
            @RequestParam(required = false) AttendanceStatus status,
            @RequestParam(required = false) Boolean lateOnly,
            @RequestParam(required = false) Boolean locationValid,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getDailyAttendance(
                date, workplaceId, organizationId, employeeName, status, lateOnly, locationValid, pageable,
                principal.getId())));
    }

    @GetMapping("/attendance/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AdminAttendanceResponse>> getAttendanceById(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getById(id, principal.getId())));
    }

    @GetMapping("/attendance/monthly")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AdminMonthlyUserSummary>>> monthlyAttendance(
            @RequestParam int year, @RequestParam int month,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getMonthlySummary(year, month, principal.getId())));
    }

    /**
     * 근태조회(일별) 화면이 로그인 계정의 레벨에 맞게 UI를 구성하기 위해 먼저 조회하는 정보.
     * 클래스 레벨 @PreAuthorize(MANAGER 이상)를 여기서는 명시적으로 완화한다(직원도 호출 가능해야 함).
     */
    @GetMapping("/attendance/scope-info")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AttendanceScopeInfoResponse>> attendanceScopeInfo(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getAttendanceScopeInfo(principal.getId())));
    }

    /**
     * 출근부(지정일) 화면. 파트장 이상 권한(레벨)만 사용할 수 있으며 서비스 내부에서 그 권한과
     * 조직 계층 범위를 함께 검증한다 — 클래스 레벨 @PreAuthorize(MANAGER 이상)를 여기서는 명시적으로 완화한다.
     */
    @GetMapping("/attendance/register-board")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AdminAttendanceBoardRow>>> registerBoard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String employeeName,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminService.getRegisterBoard(date, organizationId, employeeName, principal.getId())));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> auditLogs(
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {
        Page<AuditLog> page;
        if (actorEmail != null && !actorEmail.isBlank()) {
            page = auditLogRepository.findByActorEmailContainingIgnoreCaseOrderByCreatedAtDesc(actorEmail, pageable);
        } else if (from != null && to != null) {
            Instant fromInstant = from.toInstant(ZoneOffset.UTC);
            Instant toInstant = to.toInstant(ZoneOffset.UTC);
            page = auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(fromInstant, toInstant, pageable);
        } else {
            page = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return ResponseEntity.ok(ApiResponse.ok(page.map(AuditLogResponse::from)));
    }

    /**
     * 승인함 요청 이력 탭. 권한레벨에 따라 조회 범위가 다르다
     * (SYSADMIN/HRADMIN/PRESIDENT는 전체, 팀장/실장/본부장 등은 본인 조직 산하 + 본인, 그 외는 본인 신청건만) —
     * 클래스 레벨 @PreAuthorize(MANAGER 이상)를 이 메서드에서는 명시적으로 완화한다.
     */
    @GetMapping("/change-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ChangeRequestResponse>>> changeRequestHistory(
            @RequestParam(required = false) ChangeRequestStatus status,
            Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(changeRequestService.getHistory(status, pageable, principal.getId())));
    }

    @GetMapping("/leave-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<LeaveRequestResponse>>> leaveRequestHistory(
            @RequestParam(required = false) ChangeRequestStatus status,
            Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(leaveRequestService.getHistory(status, pageable, principal.getId())));
    }

    /**
     * 휴일/휴가 관리 캘린더용 승인된 휴가 조회. 권한레벨(LEVEL_ROLL)에 따라 조회 범위가 다르다
     * (SYSADMIN/HRADMIN/PRESIDENT는 전체, 파트장 이상 레벨은 본인 조직 산하 전체 + 본인, 직원 레벨은 본인만) —
     * 클래스 레벨 @PreAuthorize(MANAGER 이상)를 이 메서드에서는 명시적으로 완화한다.
     */
    @GetMapping("/leave-requests/calendar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> leaveRequestCalendar(
            @RequestParam int year, @RequestParam int month,
            @AuthenticationPrincipal UserPrincipal principal) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        List<LeaveRequestResponse> all = leaveRequestService.getApprovedInRange(from, to);
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(principal.getId());
        List<LeaveRequestResponse> scoped = visibleUserIds == null
                ? all
                : all.stream().filter(r -> visibleUserIds.contains(r.getRequesterId())).toList();
        return ResponseEntity.ok(ApiResponse.ok(scoped));
    }

    @GetMapping("/outside-work-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<OutsideWorkRequestResponse>>> outsideWorkRequestHistory(
            @RequestParam(required = false) ChangeRequestStatus status,
            Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(outsideWorkRequestService.getHistory(status, pageable, principal.getId())));
    }

    @GetMapping("/workplace-change-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<WorkplaceChangeRequestResponse>>> workplaceChangeRequestHistory(
            @RequestParam(required = false) ChangeRequestStatus status,
            Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(workplaceChangeRequestService.getHistory(status, pageable, principal.getId())));
    }

    @GetMapping("/work-schedule-change-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<WorkScheduleChangeRequestResponse>>> workScheduleChangeRequestHistory(
            @RequestParam(required = false) ChangeRequestStatus status,
            Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(workScheduleChangeRequestService.getHistory(status, pageable, principal.getId())));
    }

    /**
     * 근태 수동 등록. HR_ADMIN/SYSTEM_ADMIN 외에도 파트장 이상 레벨이면 본인 조직 산하 직원에 한해
     * 등록할 수 있다 — 그 권한·조직 범위 검증은 서비스 내부에서 수행하므로 여기서는 완화한다.
     */
    @PostMapping("/attendance/manual")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AdminAttendanceResponse>> createManualAttendance(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminManualAttendanceRequest request) {
        AdminAttendanceResponse response = adminService.createManualAttendance(
                principal.getId(), principal.getUsername(), request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    /**
     * 근태 보정. HR_ADMIN/SYSTEM_ADMIN 외에도 파트장 이상 레벨이면 본인 조직 산하 직원에 한해
     * 보정할 수 있다 — 그 권한·조직 범위 검증은 서비스 내부에서 수행하므로 여기서는 완화한다.
     */
    @PutMapping("/attendance/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AdminAttendanceResponse>> correctAttendance(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AdminAttendanceCorrectionRequest request) {
        AdminAttendanceResponse response = adminService.correctAttendance(
                principal.getId(), principal.getUsername(), id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/attendance/close")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> closeMonth(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminCloseMonthRequest request) {
        int count = adminService.closeMonth(principal.getId(), principal.getUsername(),
                request.getYear(), request.getMonth());
        return ResponseEntity.ok(ApiResponse.ok(count));
    }

    @PostMapping("/attendance/reopen")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> reopenMonth(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminCloseMonthRequest request) {
        int count = adminService.reopenMonth(principal.getId(), principal.getUsername(),
                request.getYear(), request.getMonth());
        return ResponseEntity.ok(ApiResponse.ok(count));
    }

    @GetMapping("/attendance/export")
    public ResponseEntity<byte[]> exportMonthlyExcel(
            @RequestParam int year, @RequestParam int month,
            @AuthenticationPrincipal UserPrincipal principal) {
        byte[] excel = adminService.exportMonthlyExcel(year, month, principal.getId());
        String filename = String.format("attendance_%d-%02d.xlsx", year, month);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(excel);
    }

    /**
     * DDL 내보내기 - 전체 DDL SQL 파일 다운로드
     * 관리자가 데이터베이스 스키마 정보를 확인하고 백업할 때 사용
     */
    @GetMapping("/ddl/export")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<byte[]> exportDDL(
            @RequestParam(required = false) String fromVersion) {
        
        String ddl;
        if (fromVersion != null && !fromVersion.isBlank()) {
            ddl = ddlExportService.exportDDLFromVersion(fromVersion);
        } else {
            ddl = ddlExportService.exportAllDDL();
        }
        
        String filename = "attendance_ddl.sql";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/sql"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(ddl.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 사용 가능한 DDL 버전 목록 조회
     */
    @GetMapping("/ddl/versions")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableDDLVersions() {
        return ResponseEntity.ok(ApiResponse.ok(ddlExportService.getAvailableVersions()));
    }
}
