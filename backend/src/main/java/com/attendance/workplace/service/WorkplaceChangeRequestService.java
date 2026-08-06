package com.attendance.workplace.service;

import com.attendance.attendance.domain.ApprovalHistory;
import com.attendance.attendance.domain.ChangeRequestStatus;
import com.attendance.attendance.dto.ApproveChangeRequestRequest;
import com.attendance.attendance.repository.ApprovalHistoryRepository;
import com.attendance.audit.service.AuditLogService;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.notification.domain.NotificationType;
import com.attendance.notification.service.NotificationService;
import com.attendance.organization.service.OrganizationScopeService;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.user.repository.UserRepository;
import com.attendance.workplace.domain.Workplace;
import com.attendance.workplace.domain.WorkplaceChangeRequest;
import com.attendance.workplace.dto.WorkplaceChangeRequestResponse;
import com.attendance.workplace.dto.WorkplaceChangeRequestSubmitRequest;
import com.attendance.workplace.repository.WorkplaceChangeRequestRepository;
import com.attendance.workplace.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 근무지 변경 요청. 직원이 신규 근무지(주소 검색 등으로 좌표 지정)를 제안하고 적용 예정일을 지정해 신청하면,
 * 승인함에서 권한레벨이 SYSADMIN/HRADMIN/PRESIDENT인 계정만 승인/반려할 수 있다(승인함의 다른 신청과 동일한 기준).
 * 승인 시 새 근무지를 실제로 생성하고, 신청 당시의 기존 배정(currentWorkplaceId, 있었던 경우)을 해제한 뒤
 * 신청자를 새 근무지에 배정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkplaceChangeRequestService {

    private static final String REQUEST_TYPE = "WORKPLACE_CHANGE_REQUEST";
    private static final Set<String> APPROVER_LEVELS = Set.of("SYSADMIN", "HRADMIN", "PRESIDENT");

    private final WorkplaceChangeRequestRepository workplaceChangeRequestRepository;
    private final WorkplaceRepository workplaceRepository;
    private final WorkplaceService workplaceService;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final OrganizationScopeService organizationScopeService;

    @Transactional
    public WorkplaceChangeRequestResponse submit(UserPrincipal principal, WorkplaceChangeRequestSubmitRequest req) {
        WorkplaceChangeRequest request = WorkplaceChangeRequest.builder()
                .requesterId(principal.getId())
                .currentWorkplaceId(req.getCurrentWorkplaceId())
                .name(req.getName())
                .address(req.getAddress())
                .detailAddress(req.getDetailAddress())
                .type(req.getType())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .radiusMeters(req.getRadiusMeters())
                .maxAccuracyMeters(req.getMaxAccuracyMeters())
                .checkInAllowed(req.isCheckInAllowed())
                .checkOutAllowed(req.isCheckOutAllowed())
                .effectiveDate(req.getEffectiveDate())
                .reason(req.getReason())
                .build();
        request = workplaceChangeRequestRepository.save(request);

        auditLogService.record(principal.getId(), principal.getUsername(), "WORKPLACE_CHANGE_REQUEST_SUBMITTED",
                "WORKPLACE_CHANGE_REQUEST", request.getId(), Map.of("name", request.getName()));
        log.info("WorkplaceChangeRequest submitted: id={} userId={}", request.getId(), principal.getId());

        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<WorkplaceChangeRequestResponse> getMyRequests(Long userId) {
        return workplaceChangeRequestRepository.findByRequesterIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 승인함 대기중 탭. 권한레벨에 따라 조회 범위가 다르다
     * (SYSADMIN/HRADMIN/PRESIDENT는 전체, 팀장/실장/본부장 등은 본인 조직 산하 + 본인, 그 외는 본인 신청건만).
     */
    @Transactional(readOnly = true)
    public List<WorkplaceChangeRequestResponse> getPendingRequests(Long actorId) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        List<WorkplaceChangeRequest> requests = visibleUserIds == null
                ? workplaceChangeRequestRepository.findByStatusOrderByCreatedAtAsc(ChangeRequestStatus.PENDING)
                : workplaceChangeRequestRepository.findByStatusAndRequesterIdInOrderByCreatedAtAsc(
                        ChangeRequestStatus.PENDING, visibleUserIds);
        return requests.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<WorkplaceChangeRequestResponse> getHistory(ChangeRequestStatus status, Pageable pageable, Long actorId) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        Page<WorkplaceChangeRequest> page;
        if (visibleUserIds == null) {
            page = status != null
                    ? workplaceChangeRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                    : workplaceChangeRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            page = status != null
                    ? workplaceChangeRequestRepository.findByStatusAndRequesterIdInOrderByCreatedAtDesc(status, visibleUserIds, pageable)
                    : workplaceChangeRequestRepository.findByRequesterIdInOrderByCreatedAtDesc(visibleUserIds, pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional
    public WorkplaceChangeRequestResponse process(UserPrincipal approver, Long requestId, ApproveChangeRequestRequest req) {
        if (!APPROVER_LEVELS.contains(approver.getLevel())) {
            throw new AttendanceException(ErrorCode.ACCESS_DENIED);
        }

        WorkplaceChangeRequest request = workplaceChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.WORKPLACE_CHANGE_REQUEST_NOT_FOUND));

        if (!request.isPending()) {
            throw new AttendanceException(ErrorCode.WORKPLACE_CHANGE_REQUEST_NOT_PENDING);
        }

        String action = req.getAction().toUpperCase();
        String dbAction;
        NotificationType notificationType;
        if ("APPROVE".equals(action)) {
            Workplace created = Workplace.builder()
                    .companyId(1L)
                    .name(request.getName())
                    .address(request.getAddress())
                    .detailAddress(request.getDetailAddress())
                    .type(request.getType())
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .radiusMeters(request.getRadiusMeters())
                    .maxAccuracyMeters(request.getMaxAccuracyMeters())
                    .checkInAllowed(request.isCheckInAllowed())
                    .checkOutAllowed(request.isCheckOutAllowed())
                    .validFrom(request.getEffectiveDate())
                    .build();
            created = workplaceRepository.save(created);
            auditLogService.record(approver.getId(), approver.getUsername(), "WORKPLACE_CREATED",
                    "WORKPLACE", created.getId(), Map.of("name", created.getName(), "source", "WORKPLACE_CHANGE_REQUEST"));

            if (request.getCurrentWorkplaceId() != null) {
                // 즉시 제거하지 않고 신규 근무지 적용 전날까지만 유효하도록 닫는다.
                // 즉시 제거하면 지금부터 effectiveDate 전날까지 근무지가 배정되지 않은 공백이 생긴다.
                workplaceService.scheduleRemovalFromWorkplace(request.getCurrentWorkplaceId(), request.getRequesterId(),
                        request.getEffectiveDate().minusDays(1), approver);
            }
            workplaceService.assignUserToWorkplace(request.getRequesterId(), created.getId(),
                    request.getEffectiveDate(), null, approver);

            request.approve(approver.getId(), created.getId());
            dbAction = "APPROVED";
            notificationType = NotificationType.WORKPLACE_CHANGE_REQUEST_APPROVED;
        } else if ("REJECT".equals(action)) {
            request.reject(approver.getId());
            dbAction = "REJECTED";
            notificationType = NotificationType.WORKPLACE_CHANGE_REQUEST_REJECTED;
        } else {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "action은 APPROVE 또는 REJECT여야 합니다.");
        }

        approvalHistoryRepository.save(ApprovalHistory.of(
                requestId, REQUEST_TYPE, approver.getId(), dbAction, req.getComment()));

        auditLogService.record(approver.getId(), approver.getUsername(), "WORKPLACE_CHANGE_REQUEST_" + dbAction,
                "WORKPLACE_CHANGE_REQUEST", requestId, Map.of("comment", req.getComment() != null ? req.getComment() : ""));

        notificationService.notify(request.getRequesterId(), notificationType,
                "근무지 변경 요청이 " + ("APPROVED".equals(dbAction) ? "승인" : "반려") + "되었습니다.",
                request.getName() + " 근무지 변경 요청이 " + ("APPROVED".equals(dbAction) ? "승인" : "반려")
                        + "되었습니다" + (req.getComment() != null && !req.getComment().isBlank() ? " (" + req.getComment() + ")" : "."),
                "WORKPLACE_CHANGE_REQUEST", requestId);

        log.info("WorkplaceChangeRequest {} by approverId={} requestId={}", action, approver.getId(), requestId);
        return toResponse(request);
    }

    private WorkplaceChangeRequestResponse toResponse(WorkplaceChangeRequest r) {
        String requesterName = userRepository.findById(r.getRequesterId()).map(User::getName).orElse(null);
        String currentWorkplaceName = r.getCurrentWorkplaceId() != null
                ? workplaceRepository.findById(r.getCurrentWorkplaceId()).map(Workplace::getName).orElse(null)
                : null;
        String approverName = resolveApproverName(r.getCurrentApproverId());
        return WorkplaceChangeRequestResponse.from(r, requesterName, currentWorkplaceName, approverName);
    }

    private String resolveApproverName(Long approverId) {
        return approverId == null ? null : userRepository.findById(approverId).map(User::getName).orElse(null);
    }
}
