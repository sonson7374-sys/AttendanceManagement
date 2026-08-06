package com.attendance.outsidework.service;

import com.attendance.attendance.domain.AttendanceRecord;
import com.attendance.attendance.domain.AttendanceStatus;
import com.attendance.attendance.domain.ApprovalHistory;
import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.attendance.dto.ApproveChangeRequestRequest;
import com.attendance.attendance.repository.ApprovalHistoryRepository;
import com.attendance.attendance.repository.AttendanceRecordRepository;
import com.attendance.audit.service.AuditLogService;
import com.attendance.common.config.AppConfig;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.notification.domain.NotificationType;
import com.attendance.notification.service.NotificationService;
import com.attendance.organization.service.OrganizationScopeService;
import com.attendance.outsidework.domain.OutsideWorkRequest;
import com.attendance.outsidework.dto.OutsideWorkRequestResponse;
import com.attendance.outsidework.dto.OutsideWorkRequestSubmitRequest;
import com.attendance.outsidework.repository.OutsideWorkRequestRepository;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutsideWorkRequestService {

    private static final String REQUEST_TYPE = "OUTSIDE_WORK_REQUEST";

    // 승인/반려 처리는 권한레벨(level, 그룹코드 LEVEL_ROLL)이 이 집합에 속한 계정만 가능하다.
    private static final Set<String> APPROVER_LEVELS = Set.of("SYSADMIN", "HRADMIN", "PRESIDENT");

    private final OutsideWorkRequestRepository outsideWorkRequestRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final AttendanceRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final OrganizationScopeService organizationScopeService;

    @Transactional
    public OutsideWorkRequestResponse submit(UserPrincipal principal, OutsideWorkRequestSubmitRequest req) {
        OutsideWorkRequest request = OutsideWorkRequest.builder()
                .requesterId(principal.getId())
                .requestType(req.getRequestType())
                .startAt(req.getStartAt().toInstant())
                .endAt(req.getEndAt().toInstant())
                .reason(req.getReason())
                .destinationAddress(req.getDestinationAddress())
                .destinationLatitude(req.getDestinationLatitude())
                .destinationLongitude(req.getDestinationLongitude())
                .tempRadiusMeters(req.getTempRadiusMeters())
                .visitPurpose(req.getVisitPurpose())
                .clientName(req.getClientName())
                .expectedReturnAt(req.getExpectedReturnAt() != null ? req.getExpectedReturnAt().toInstant() : null)
                .build();
        request = outsideWorkRequestRepository.save(request);

        auditLogService.record(principal.getId(), principal.getUsername(),
                "OUTSIDE_WORK_REQUEST_SUBMITTED", "OUTSIDE_WORK_REQUEST", request.getId(),
                Map.of("requestType", req.getRequestType().name(), "reason", req.getReason()));

        log.info("OutsideWorkRequest submitted: id={} userId={} type={}", request.getId(), principal.getId(), req.getRequestType());
        return OutsideWorkRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<OutsideWorkRequestResponse> getMyRequests(Long userId) {
        return outsideWorkRequestRepository.findByRequesterIdOrderByCreatedAtDesc(userId).stream()
                .map(OutsideWorkRequestResponse::from)
                .toList();
    }

    /**
     * 승인함 대기중 탭. 권한레벨에 따라 조회 범위가 다르다
     * (SYSADMIN/HRADMIN/PRESIDENT는 전체, 팀장/실장/본부장 등은 본인 조직 산하 + 본인, 그 외는 본인 신청건만).
     */
    @Transactional(readOnly = true)
    public List<OutsideWorkRequestResponse> getPendingRequests(Long actorId) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        List<OutsideWorkRequest> requests = visibleUserIds == null
                ? outsideWorkRequestRepository.findByStatusOrderByCreatedAtAsc(ChangeRequestStatus.PENDING)
                : outsideWorkRequestRepository.findByStatusAndRequesterIdInOrderByCreatedAtAsc(
                        ChangeRequestStatus.PENDING, visibleUserIds);
        return requests.stream().map(this::toResponseWithRequesterName).toList();
    }

    @Transactional(readOnly = true)
    public Page<OutsideWorkRequestResponse> getHistory(ChangeRequestStatus status, Pageable pageable, Long actorId) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        Page<OutsideWorkRequest> page;
        if (visibleUserIds == null) {
            page = status != null
                    ? outsideWorkRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                    : outsideWorkRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            page = status != null
                    ? outsideWorkRequestRepository.findByStatusAndRequesterIdInOrderByCreatedAtDesc(status, visibleUserIds, pageable)
                    : outsideWorkRequestRepository.findByRequesterIdInOrderByCreatedAtDesc(visibleUserIds, pageable);
        }
        return page.map(this::toResponseWithRequesterName);
    }

    private OutsideWorkRequestResponse toResponseWithRequesterName(OutsideWorkRequest req) {
        String requesterName = userRepository.findById(req.getRequesterId())
                .map(User::getName)
                .orElse(null);
        String approverName = resolveApproverName(req.getCurrentApproverId());
        return OutsideWorkRequestResponse.from(req, requesterName, approverName);
    }

    private String resolveApproverName(Long approverId) {
        return approverId == null ? null : userRepository.findById(approverId).map(User::getName).orElse(null);
    }

    @Transactional
    public OutsideWorkRequestResponse process(UserPrincipal approver, Long requestId, ApproveChangeRequestRequest req) {
        if (!APPROVER_LEVELS.contains(approver.getLevel())) {
            throw new AttendanceException(ErrorCode.ACCESS_DENIED);
        }

        OutsideWorkRequest request = outsideWorkRequestRepository.findById(requestId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.OUTSIDE_WORK_REQUEST_NOT_FOUND));

        if (!request.isPending()) {
            throw new AttendanceException(ErrorCode.OUTSIDE_WORK_REQUEST_NOT_PENDING);
        }

        String action = req.getAction().toUpperCase();
        String dbAction;
        NotificationType notificationType;
        if ("APPROVE".equals(action)) {
            request.approve(approver.getId());
            applyStatusToAttendanceRecords(request);
            dbAction = "APPROVED";
            notificationType = NotificationType.OUTSIDE_WORK_REQUEST_APPROVED;
        } else if ("REJECT".equals(action)) {
            request.reject(approver.getId());
            dbAction = "REJECTED";
            notificationType = NotificationType.OUTSIDE_WORK_REQUEST_REJECTED;
        } else {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "action은 APPROVE 또는 REJECT여야 합니다.");
        }

        approvalHistoryRepository.save(ApprovalHistory.of(
                requestId, REQUEST_TYPE, approver.getId(), dbAction, req.getComment()));

        auditLogService.record(approver.getId(), approver.getUsername(),
                "OUTSIDE_WORK_REQUEST_" + dbAction, "OUTSIDE_WORK_REQUEST", requestId,
                Map.of("comment", req.getComment() != null ? req.getComment() : ""));

        notificationService.notify(request.getRequesterId(), notificationType,
                "외근·출장·재택 신청이 " + ("APPROVED".equals(dbAction) ? "승인" : "반려") + "되었습니다.",
                request.getRequestType() + " 신청이 " + ("APPROVED".equals(dbAction) ? "승인" : "반려")
                        + "되었습니다" + (req.getComment() != null && !req.getComment().isBlank() ? " (" + req.getComment() + ")" : "."),
                "OUTSIDE_WORK_REQUEST", requestId);

        log.info("OutsideWorkRequest {} by approverId={} requestId={}", action, approver.getId(), requestId);
        return OutsideWorkRequestResponse.from(request);
    }

    private void applyStatusToAttendanceRecords(OutsideWorkRequest request) {
        AttendanceStatus status = AttendanceStatus.valueOf(request.getRequestType().name());
        LocalDate from = request.getStartAt().atZone(AppConfig.SEOUL).toLocalDate();
        LocalDate to = request.getEndAt().atZone(AppConfig.SEOUL).toLocalDate();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            LocalDate workDate = date;
            recordRepository.findByUserIdAndWorkDate(request.getRequesterId(), workDate).ifPresentOrElse(
                    record -> record.applyAdminCorrection(null, null, null, status, null, null, null, null, null),
                    () -> recordRepository.save(AttendanceRecord.createManual(
                            request.getRequesterId(), workDate, null, null, null,
                            status, null, null, null, false, false))
            );
        }
    }
}
