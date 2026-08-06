package com.attendance.leave.service;

import com.attendance.attendance.domain.AttendanceRecord;
import com.attendance.attendance.domain.AttendanceStatus;
import com.attendance.attendance.domain.ApprovalHistory;
import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.attendance.repository.ApprovalHistoryRepository;
import com.attendance.attendance.repository.AttendanceRecordRepository;
import com.attendance.audit.service.AuditLogService;
import com.attendance.common.config.AppConfig;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.leave.domain.LeaveRequest;
import com.attendance.leave.domain.LeaveRequestType;
import com.attendance.leave.dto.LeaveRequestResponse;
import com.attendance.leave.dto.LeaveRequestSubmitRequest;
import com.attendance.attendance.dto.ApproveChangeRequestRequest;
import com.attendance.leave.repository.LeaveRequestRepository;
import com.attendance.notification.domain.NotificationType;
import com.attendance.notification.service.NotificationService;
import com.attendance.organization.service.OrganizationScopeService;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private static final String REQUEST_TYPE = "LEAVE_REQUEST";

    /**
     * 근태 상태를 LEAVE로 반영하는 신청 유형. 연장근무/휴일근무는 근무일 상태를 바꾸지 않는 승인 전용 신청이다.
     * 다른 화면(출근부 지정일 등)에서도 "휴가로 볼 유형"을 동일하게 판단해야 하므로 public으로 공개한다.
     */
    public static final Set<LeaveRequestType> ATTENDANCE_LEAVE_TYPES = EnumSet.of(
            LeaveRequestType.ANNUAL, LeaveRequestType.HALF_DAY, LeaveRequestType.HOURLY,
            LeaveRequestType.SICK, LeaveRequestType.OFFICIAL);

    // 승인/반려 처리는 권한레벨(level, 그룹코드 LEVEL_ROLL)이 이 집합에 속한 계정만 가능하다.
    private static final Set<String> APPROVER_LEVELS = Set.of("SYSADMIN", "HRADMIN", "PRESIDENT");

    private final LeaveRequestRepository leaveRequestRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final AttendanceRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final OrganizationScopeService organizationScopeService;

    @Transactional
    public LeaveRequestResponse submit(UserPrincipal principal, LeaveRequestSubmitRequest req) {
        User requester = userRepository.findById(principal.getId())
                .orElseThrow(() -> new AttendanceException(ErrorCode.USER_NOT_FOUND));
        LeaveRequest request = LeaveRequest.create(
                principal.getId(), requester.getEmployeeNumber(), req.getRequestType(),
                req.getStartAt().toInstant(), req.getEndAt().toInstant(), req.getReason());
        request = leaveRequestRepository.save(request);

        auditLogService.record(principal.getId(), principal.getUsername(),
                "LEAVE_REQUEST_SUBMITTED", "LEAVE_REQUEST", request.getId(),
                Map.of("requestType", req.getRequestType().name(), "reason", req.getReason()));

        log.info("LeaveRequest submitted: id={} userId={} type={}", request.getId(), principal.getId(), req.getRequestType());
        return LeaveRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getMyRequests(Long userId) {
        return leaveRequestRepository.findByRequesterIdOrderByCreatedAtDesc(userId).stream()
                .map(LeaveRequestResponse::from)
                .toList();
    }

    /**
     * 승인함 대기중 탭. 권한레벨에 따라 조회 범위가 다르다
     * (SYSADMIN/HRADMIN/PRESIDENT는 전체, 팀장/실장/본부장 등은 본인 조직 산하 + 본인, 그 외는 본인 신청건만).
     */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getPendingRequests(Long actorId) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        List<LeaveRequest> requests = visibleUserIds == null
                ? leaveRequestRepository.findByStatusOrderByCreatedAtAsc(ChangeRequestStatus.PENDING)
                : leaveRequestRepository.findByStatusAndRequesterIdInOrderByCreatedAtAsc(
                        ChangeRequestStatus.PENDING, visibleUserIds);
        return requests.stream().map(this::toResponseWithRequesterName).toList();
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getHistory(ChangeRequestStatus status, Pageable pageable, Long actorId) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        Page<LeaveRequest> page;
        if (visibleUserIds == null) {
            page = status != null
                    ? leaveRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                    : leaveRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            page = status != null
                    ? leaveRequestRepository.findByStatusAndRequesterIdInOrderByCreatedAtDesc(status, visibleUserIds, pageable)
                    : leaveRequestRepository.findByRequesterIdInOrderByCreatedAtDesc(visibleUserIds, pageable);
        }
        return page.map(this::toResponseWithRequesterName);
    }

    /** 휴일/휴가 관리 캘린더 표시용: from~to 기간과 겹치는 승인된 휴가 신청을 조회한다. */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getApprovedInRange(LocalDate from, LocalDate to) {
        Instant rangeStart = from.atStartOfDay(AppConfig.SEOUL).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(AppConfig.SEOUL).toInstant();
        return leaveRequestRepository
                .findByStatusAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtAsc(
                        ChangeRequestStatus.APPROVED, rangeEnd, rangeStart)
                .stream()
                .map(this::toResponseWithRequesterName)
                .toList();
    }

    private LeaveRequestResponse toResponseWithRequesterName(LeaveRequest req) {
        String requesterName = userRepository.findById(req.getRequesterId())
                .map(User::getName)
                .orElse(null);
        String approverName = resolveApproverName(req.getCurrentApproverId());
        return LeaveRequestResponse.from(req, requesterName, approverName);
    }

    private String resolveApproverName(Long approverId) {
        return approverId == null ? null : userRepository.findById(approverId).map(User::getName).orElse(null);
    }

    @Transactional
    public LeaveRequestResponse process(UserPrincipal approver, Long requestId, ApproveChangeRequestRequest req) {
        if (!APPROVER_LEVELS.contains(approver.getLevel())) {
            throw new AttendanceException(ErrorCode.ACCESS_DENIED);
        }

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.LEAVE_REQUEST_NOT_FOUND));

        if (!request.isPending()) {
            throw new AttendanceException(ErrorCode.LEAVE_REQUEST_NOT_PENDING);
        }

        String action = req.getAction().toUpperCase();
        String dbAction;
        NotificationType notificationType;
        if ("APPROVE".equals(action)) {
            request.approve(approver.getId());
            if (ATTENDANCE_LEAVE_TYPES.contains(request.getRequestType())) {
                applyLeaveToAttendanceRecords(request);
            }
            dbAction = "APPROVED";
            notificationType = NotificationType.LEAVE_REQUEST_APPROVED;
        } else if ("REJECT".equals(action)) {
            request.reject(approver.getId());
            dbAction = "REJECTED";
            notificationType = NotificationType.LEAVE_REQUEST_REJECTED;
        } else {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "action은 APPROVE 또는 REJECT여야 합니다.");
        }

        approvalHistoryRepository.save(ApprovalHistory.of(
                requestId, REQUEST_TYPE, approver.getId(), dbAction, req.getComment()));

        auditLogService.record(approver.getId(), approver.getUsername(),
                "LEAVE_REQUEST_" + dbAction, "LEAVE_REQUEST", requestId,
                Map.of("comment", req.getComment() != null ? req.getComment() : ""));

        notificationService.notify(request.getRequesterId(), notificationType,
                "휴가 신청이 " + ("APPROVED".equals(dbAction) ? "승인" : "반려") + "되었습니다.",
                request.getRequestType() + " 신청이 " + ("APPROVED".equals(dbAction) ? "승인" : "반려")
                        + "되었습니다" + (req.getComment() != null && !req.getComment().isBlank() ? " (" + req.getComment() + ")" : "."),
                "LEAVE_REQUEST", requestId);

        log.info("LeaveRequest {} by approverId={} requestId={}", action, approver.getId(), requestId);
        return LeaveRequestResponse.from(request);
    }

    private void applyLeaveToAttendanceRecords(LeaveRequest request) {
        LocalDate from = request.getStartAt().atZone(AppConfig.SEOUL).toLocalDate();
        LocalDate to = request.getEndAt().atZone(AppConfig.SEOUL).toLocalDate();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            LocalDate workDate = date;
            recordRepository.findByUserIdAndWorkDate(request.getRequesterId(), workDate).ifPresentOrElse(
                    record -> record.applyAdminCorrection(null, null, null, AttendanceStatus.LEAVE, null, null, null, null, null),
                    () -> recordRepository.save(AttendanceRecord.createManual(
                            request.getRequesterId(), workDate, null, null, null,
                            AttendanceStatus.LEAVE, null, null, null, false, false))
            );
        }
    }
}
