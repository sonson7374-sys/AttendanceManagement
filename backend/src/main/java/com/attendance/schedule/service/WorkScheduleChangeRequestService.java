package com.attendance.schedule.service;

import com.attendance.attendance.domain.ApprovalHistory;
import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.attendance.dto.ApproveChangeRequestRequest;
import com.attendance.attendance.repository.ApprovalHistoryRepository;
import com.attendance.audit.service.AuditLogService;
import com.attendance.common.config.AppConfig;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.notification.domain.NotificationType;
import com.attendance.notification.service.NotificationService;
import com.attendance.organization.service.OrganizationScopeService;
import com.attendance.schedule.domain.WorkSchedule;
import com.attendance.schedule.domain.WorkScheduleChangeRequest;
import com.attendance.schedule.dto.WorkScheduleChangeRequestResponse;
import com.attendance.schedule.dto.WorkScheduleChangeRequestSubmitRequest;
import com.attendance.schedule.repository.WorkScheduleChangeRequestRepository;
import com.attendance.schedule.repository.WorkScheduleRepository;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 근무제 변경 요청. 직원이 기존 근무제 목록 중 하나를 선택하고 적용 예정월을 지정해 신청하면,
 * 승인함에서 권한레벨이 SYSADMIN/HRADMIN/PRESIDENT인 계정만 승인/반려할 수 있다(승인함의 다른 신청과 동일한 기준).
 * 승인 시 신청자를 선택한 근무제로 배정한다(기존 배정은 WorkScheduleService가 자동으로 대체한다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkScheduleChangeRequestService {

    private static final String REQUEST_TYPE = "WORK_SCHEDULE_CHANGE_REQUEST";
    private static final Set<String> APPROVER_LEVELS = Set.of("SYSADMIN", "HRADMIN", "PRESIDENT");

    private final WorkScheduleChangeRequestRepository workScheduleChangeRequestRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final WorkScheduleService workScheduleService;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final OrganizationScopeService organizationScopeService;

    @Transactional
    public WorkScheduleChangeRequestResponse submit(UserPrincipal principal, WorkScheduleChangeRequestSubmitRequest req) {
        YearMonth nextMonth = YearMonth.now(AppConfig.SEOUL).plusMonths(1);
        if (req.getEffectiveMonth().isBefore(nextMonth)) {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "적용 예정월은 다음 달 이후여야 합니다.");
        }
        WorkSchedule targetSchedule = workScheduleRepository.findById(req.getTargetWorkScheduleId())
                .filter(WorkSchedule::isActive)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND));

        WorkScheduleChangeRequest request = WorkScheduleChangeRequest.builder()
                .requesterId(principal.getId())
                .currentWorkScheduleId(req.getCurrentWorkScheduleId())
                .targetWorkScheduleId(targetSchedule.getId())
                .effectiveMonth(req.getEffectiveMonth().atDay(1))
                .reason(req.getReason())
                .build();
        request = workScheduleChangeRequestRepository.save(request);

        auditLogService.record(principal.getId(), principal.getUsername(), "WORK_SCHEDULE_CHANGE_REQUEST_SUBMITTED",
                "WORK_SCHEDULE_CHANGE_REQUEST", request.getId(), Map.of("targetWorkScheduleId", targetSchedule.getId()));
        log.info("WorkScheduleChangeRequest submitted: id={} userId={}", request.getId(), principal.getId());

        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<WorkScheduleChangeRequestResponse> getMyRequests(Long userId) {
        return workScheduleChangeRequestRepository.findByRequesterIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkScheduleChangeRequestResponse> getPendingRequests(Long actorId) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        List<WorkScheduleChangeRequest> requests = visibleUserIds == null
                ? workScheduleChangeRequestRepository.findByStatusOrderByCreatedAtAsc(ChangeRequestStatus.PENDING)
                : workScheduleChangeRequestRepository.findByStatusAndRequesterIdInOrderByCreatedAtAsc(
                        ChangeRequestStatus.PENDING, visibleUserIds);
        return requests.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<WorkScheduleChangeRequestResponse> getHistory(ChangeRequestStatus status, Pageable pageable, Long actorId) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        Page<WorkScheduleChangeRequest> page;
        if (visibleUserIds == null) {
            page = status != null
                    ? workScheduleChangeRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                    : workScheduleChangeRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            page = status != null
                    ? workScheduleChangeRequestRepository.findByStatusAndRequesterIdInOrderByCreatedAtDesc(status, visibleUserIds, pageable)
                    : workScheduleChangeRequestRepository.findByRequesterIdInOrderByCreatedAtDesc(visibleUserIds, pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional
    public WorkScheduleChangeRequestResponse process(UserPrincipal approver, Long requestId, ApproveChangeRequestRequest req) {
        if (!APPROVER_LEVELS.contains(approver.getLevel())) {
            throw new AttendanceException(ErrorCode.ACCESS_DENIED);
        }

        WorkScheduleChangeRequest request = workScheduleChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.WORK_SCHEDULE_CHANGE_REQUEST_NOT_FOUND));

        if (!request.isPending()) {
            throw new AttendanceException(ErrorCode.WORK_SCHEDULE_CHANGE_REQUEST_NOT_PENDING);
        }

        String action = req.getAction().toUpperCase();
        String dbAction;
        NotificationType notificationType;
        if ("APPROVE".equals(action)) {
            workScheduleService.assignWorkSchedule(request.getRequesterId(), request.getTargetWorkScheduleId(),
                    request.getEffectiveMonth(), approver);

            request.approve(approver.getId());
            dbAction = "APPROVED";
            notificationType = NotificationType.WORK_SCHEDULE_CHANGE_REQUEST_APPROVED;
        } else if ("REJECT".equals(action)) {
            request.reject(approver.getId());
            dbAction = "REJECTED";
            notificationType = NotificationType.WORK_SCHEDULE_CHANGE_REQUEST_REJECTED;
        } else {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "action은 APPROVE 또는 REJECT여야 합니다.");
        }

        approvalHistoryRepository.save(ApprovalHistory.of(
                requestId, REQUEST_TYPE, approver.getId(), dbAction, req.getComment()));

        auditLogService.record(approver.getId(), approver.getUsername(), "WORK_SCHEDULE_CHANGE_REQUEST_" + dbAction,
                "WORK_SCHEDULE_CHANGE_REQUEST", requestId, Map.of("comment", req.getComment() != null ? req.getComment() : ""));

        String targetScheduleName = workScheduleRepository.findById(request.getTargetWorkScheduleId())
                .map(WorkSchedule::getName).orElse("");
        notificationService.notify(request.getRequesterId(), notificationType,
                "근무제 변경 요청이 " + ("APPROVED".equals(dbAction) ? "승인" : "반려") + "되었습니다.",
                targetScheduleName + " 근무제 변경 요청이 " + ("APPROVED".equals(dbAction) ? "승인" : "반려")
                        + "되었습니다" + (req.getComment() != null && !req.getComment().isBlank() ? " (" + req.getComment() + ")" : "."),
                "WORK_SCHEDULE_CHANGE_REQUEST", requestId);

        log.info("WorkScheduleChangeRequest {} by approverId={} requestId={}", action, approver.getId(), requestId);
        return toResponse(request);
    }

    private WorkScheduleChangeRequestResponse toResponse(WorkScheduleChangeRequest r) {
        String requesterName = userRepository.findById(r.getRequesterId()).map(User::getName).orElse(null);
        String currentWorkScheduleName = r.getCurrentWorkScheduleId() != null
                ? workScheduleRepository.findById(r.getCurrentWorkScheduleId()).map(WorkSchedule::getName).orElse(null)
                : null;
        String targetWorkScheduleName = workScheduleRepository.findById(r.getTargetWorkScheduleId())
                .map(WorkSchedule::getName).orElse(null);
        String approverName = resolveApproverName(r.getCurrentApproverId());
        return WorkScheduleChangeRequestResponse.from(r, requesterName, currentWorkScheduleName, targetWorkScheduleName, approverName);
    }

    private String resolveApproverName(Long approverId) {
        return approverId == null ? null : userRepository.findById(approverId).map(User::getName).orElse(null);
    }
}
