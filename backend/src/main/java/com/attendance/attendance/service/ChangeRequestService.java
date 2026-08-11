package com.attendance.attendance.service;

import com.attendance.attendance.domain.*;
import com.attendance.attendance.dto.*;
import com.attendance.attendance.repository.*;
import com.attendance.audit.service.AuditLogService;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.notification.domain.NotificationType;
import com.attendance.notification.service.NotificationService;
import com.attendance.organization.service.OrganizationScopeService;
import com.attendance.schedule.domain.WorkSchedule;
import com.attendance.schedule.service.WorkScheduleService;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeRequestService {

    // 승인/반려 처리는 원칙적으로 권한레벨(level, 그룹코드 LEVEL_ROLL)이 이 집합에 속한 계정만 가능하다.
    private static final Set<String> APPROVER_LEVELS = Set.of("SYSADMIN", "HRADMIN", "PRESIDENT");

    // 다만 출근/퇴근시간 수정·결근처리 수정은 파트장 이상 레벨이면 본인 조직 산하 신청에 대해 직접 승인/반려할 수 있다
    // (근무지 변경·지각 정정은 영향 범위가 더 커서 여전히 APPROVER_LEVELS만 처리 가능).
    private static final Set<ChangeRequestType> PART_LEAD_APPROVABLE_TYPES = Set.of(
            ChangeRequestType.CHECK_IN_TIME, ChangeRequestType.CHECK_OUT_TIME, ChangeRequestType.ABSENT_CORRECTION);

    private final ChangeRequestRepository changeRequestRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final AttendanceRecordRepository recordRepository;
    private final WorkScheduleService workScheduleService;
    private final AttendanceScheduleEvaluator scheduleEvaluator;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final OrganizationScopeService organizationScopeService;

    @Transactional
    public ChangeRequestResponse submit(UserPrincipal principal, ChangeRequestSubmitRequest req) {
        AttendanceRecord record = recordRepository.findById(req.getRecordId())
                .orElseThrow(() -> new AttendanceException(ErrorCode.ATTENDANCE_RECORD_NOT_FOUND));

        if (!record.getUserId().equals(principal.getId())) {
            throw new AttendanceException(ErrorCode.CHANGE_REQUEST_ACCESS_DENIED);
        }

        AttendanceChangeRequest changeRequest = AttendanceChangeRequest.create(
                principal.getId(),
                record.getId(),
                record.getWorkDate(),
                req.getChangeType(),
                req.getRequestedCheckIn() != null ? req.getRequestedCheckIn().toInstant() : null,
                req.getRequestedCheckOut() != null ? req.getRequestedCheckOut().toInstant() : null,
                req.getRequestedWorkplaceId(),
                req.getReason());

        changeRequest = changeRequestRepository.save(changeRequest);
        log.info("ChangeRequest submitted: id={} userId={} type={}", changeRequest.getId(), principal.getId(), req.getChangeType());

        auditLogService.record(principal.getId(), principal.getUsername(),
                "CHANGE_REQUEST_SUBMITTED", "CHANGE_REQUEST", changeRequest.getId(),
                Map.of("changeType", req.getChangeType().name(), "reason", req.getReason()));

        return ChangeRequestResponse.from(changeRequest);
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestResponse> getMyRequests(Long userId) {
        return changeRequestRepository.findByRequesterIdOrderByCreatedAtDesc(userId).stream()
                .map(ChangeRequestResponse::from)
                .toList();
    }

    /**
     * 승인함 대기중 탭. 권한레벨에 따라 조회 범위가 다르다
     * (SYSADMIN/HRADMIN/PRESIDENT는 전체, 팀장/실장/본부장 등은 본인 조직 산하 + 본인, 그 외는 본인 신청건만).
     */
    @Transactional(readOnly = true)
    public List<ChangeRequestResponse> getPendingRequests(Long actorId) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        List<AttendanceChangeRequest> requests = visibleUserIds == null
                ? changeRequestRepository.findByStatusOrderByCreatedAtAsc(ChangeRequestStatus.PENDING)
                : changeRequestRepository.findByStatusAndRequesterIdInOrderByCreatedAtAsc(
                        ChangeRequestStatus.PENDING, visibleUserIds);
        return requests.stream().map(this::toResponseWithRequesterName).toList();
    }

    @Transactional(readOnly = true)
    public Page<ChangeRequestResponse> getHistory(ChangeRequestStatus status, Pageable pageable, Long actorId) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        Page<AttendanceChangeRequest> page;
        if (visibleUserIds == null) {
            page = status != null
                    ? changeRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                    : changeRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            page = status != null
                    ? changeRequestRepository.findByStatusAndRequesterIdInOrderByCreatedAtDesc(status, visibleUserIds, pageable)
                    : changeRequestRepository.findByRequesterIdInOrderByCreatedAtDesc(visibleUserIds, pageable);
        }
        return page.map(this::toResponseWithRequesterName);
    }

    private ChangeRequestResponse toResponseWithRequesterName(AttendanceChangeRequest req) {
        String requesterName = userRepository.findById(req.getRequesterId())
                .map(User::getName)
                .orElse(null);
        String approverName = resolveApproverName(req.getCurrentApproverId());
        return ChangeRequestResponse.from(req, requesterName, approverName);
    }

    private String resolveApproverName(Long approverId) {
        return approverId == null ? null : userRepository.findById(approverId).map(User::getName).orElse(null);
    }

    @Transactional
    public ChangeRequestResponse process(UserPrincipal approver, Long requestId, ApproveChangeRequestRequest req) {
        AttendanceChangeRequest changeRequest = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.CHANGE_REQUEST_NOT_FOUND));

        if (!APPROVER_LEVELS.contains(approver.getLevel())) {
            boolean partLeadOrAbove = organizationScopeService.isPartLeadOrAbove(approver.getLevel());
            boolean approvableType = PART_LEAD_APPROVABLE_TYPES.contains(changeRequest.getChangeType());
            if (!partLeadOrAbove || !approvableType) {
                throw new AttendanceException(ErrorCode.ACCESS_DENIED);
            }
            Set<Long> managedUserIds = organizationScopeService.resolveManagedUserIds(approver.getId());
            if (managedUserIds != null && !managedUserIds.contains(changeRequest.getRequesterId())) {
                throw new AttendanceException(ErrorCode.ACCESS_DENIED);
            }
        }

        if (!changeRequest.isPending()) {
            throw new AttendanceException(ErrorCode.CHANGE_REQUEST_NOT_PENDING);
        }

        if (changeRequest.getRecordId() != null) {
            recordRepository.findById(changeRequest.getRecordId()).ifPresent(record -> {
                if (record.isClosed()) {
                    throw new AttendanceException(ErrorCode.ATTENDANCE_CLOSED);
                }
            });
        }

        String action = req.getAction().toUpperCase();
        String dbAction; // DB CHECK 제약: APPROVED, REJECTED, CANCELED
        NotificationType notificationType;
        if ("APPROVE".equals(action)) {
            changeRequest.approve(approver.getId());
            applyApprovedChanges(changeRequest);
            dbAction = "APPROVED";
            notificationType = NotificationType.CHANGE_REQUEST_APPROVED;
        } else if ("REJECT".equals(action)) {
            changeRequest.reject(approver.getId());
            dbAction = "REJECTED";
            notificationType = NotificationType.CHANGE_REQUEST_REJECTED;
        } else {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "action은 APPROVE 또는 REJECT여야 합니다.");
        }

        approvalHistoryRepository.save(ApprovalHistory.of(
                requestId, approver.getId(), dbAction, req.getComment()));

        auditLogService.record(approver.getId(), approver.getUsername(),
                "CHANGE_REQUEST_" + dbAction, "CHANGE_REQUEST", requestId,
                Map.of("comment", req.getComment() != null ? req.getComment() : ""));

        notificationService.notify(changeRequest.getRequesterId(), notificationType,
                "근태 수정 요청이 " + ("APPROVED".equals(dbAction) ? "승인" : "반려") + "되었습니다.",
                changeRequest.getTargetDate() + " 근태 수정 요청이 " + ("APPROVED".equals(dbAction) ? "승인" : "반려")
                        + "되었습니다" + (req.getComment() != null && !req.getComment().isBlank() ? " (" + req.getComment() + ")" : "."),
                "CHANGE_REQUEST", requestId);

        log.info("ChangeRequest {} by approverId={} requestId={}", action, approver.getId(), requestId);
        return ChangeRequestResponse.from(changeRequest);
    }

    private void applyApprovedChanges(AttendanceChangeRequest req) {
        if (req.getRecordId() == null) return;

        recordRepository.findById(req.getRecordId()).ifPresent(record -> {
            switch (req.getChangeType()) {
                case CHECK_IN_TIME -> {
                    if (req.getRequestedCheckIn() != null) {
                        applyCheckInCorrection(record, req.getRequestedCheckIn());
                    }
                }
                case CHECK_OUT_TIME -> {
                    if (req.getRequestedCheckOut() != null) {
                        applyCheckOutCorrection(record, req.getRequestedCheckOut());
                    }
                }
                case WORKPLACE_CHANGE -> {
                    if (req.getRequestedWorkplaceId() != null) {
                        record.correctWorkplace(req.getRequestedWorkplaceId());
                    }
                }
                default -> log.info("ChangeType {} does not auto-apply to record", req.getChangeType());
            }
        });
    }

    private void applyCheckInCorrection(AttendanceRecord record, java.time.Instant newCheckInAt) {
        WorkSchedule schedule = workScheduleService.resolveSchedule(record.getUserId(), record.getWorkDate());
        boolean late = scheduleEvaluator.isLate(newCheckInAt, record.getWorkDate(), schedule);
        record.correctCheckIn(newCheckInAt, late);
    }

    private void applyCheckOutCorrection(AttendanceRecord record, java.time.Instant newCheckOutAt) {
        if (record.getCheckInAt() == null) {
            return;
        }
        WorkSchedule schedule = workScheduleService.resolveSchedule(record.getUserId(), record.getWorkDate());
        long totalMinutes = Duration.between(record.getCheckInAt(), newCheckOutAt).toMinutes();
        long breakMinutes = schedule.getBreakMinutes();
        long workMinutes = Math.max(0, totalMinutes - breakMinutes);
        // 잔업시간은 "근무스케줄 외 근무시간"(조기 출근 + 늦은 퇴근)과 같은 기준으로 계산해 저장한다.
        long overtimeMinutes = scheduleEvaluator.computeOutsideScheduleMinutes(
                record.getCheckInAt(), newCheckOutAt, record.getWorkDate(), schedule);
        boolean earlyLeave = scheduleEvaluator.isEarlyLeave(newCheckOutAt, schedule);
        record.correctCheckOut(newCheckOutAt, (int) workMinutes, (int) breakMinutes,
                (int) overtimeMinutes, earlyLeave);
    }
}
