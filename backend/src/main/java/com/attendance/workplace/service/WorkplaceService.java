package com.attendance.workplace.service;

import com.attendance.audit.service.AuditLogService;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.user.dto.UserResponse;
import com.attendance.user.repository.UserRepository;
import com.attendance.workplace.domain.UserWorkplace;
import com.attendance.workplace.domain.Workplace;
import com.attendance.workplace.dto.WorkplaceRequest;
import com.attendance.workplace.dto.WorkplaceResponse;
import com.attendance.workplace.repository.UserWorkplaceRepository;
import com.attendance.workplace.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkplaceService {

    private final WorkplaceRepository workplaceRepository;
    private final UserWorkplaceRepository userWorkplaceRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<WorkplaceResponse> listByCompany(Long companyId, boolean includeInactive) {
        List<Workplace> workplaces = includeInactive
                ? workplaceRepository.findByCompanyId(companyId)
                : workplaceRepository.findByCompanyIdAndActive(companyId, true);
        return workplaces.stream().map(WorkplaceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public WorkplaceResponse getWorkplace(Long id) {
        return WorkplaceResponse.from(findById(id));
    }

    @Transactional
    public WorkplaceResponse createWorkplace(WorkplaceRequest request, UserPrincipal actor) {
        Workplace workplace = Workplace.builder()
                .companyId(request.getCompanyId())
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
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .build();
        workplace = workplaceRepository.save(workplace);
        auditLogService.record(actor.getId(), actor.getUsername(), "WORKPLACE_CREATED",
                "WORKPLACE", workplace.getId(),
                Map.of("name", workplace.getName(), "address", String.valueOf(workplace.getAddress())));
        return WorkplaceResponse.from(workplace);
    }

    @Transactional
    public WorkplaceResponse updateWorkplace(Long id, WorkplaceRequest request, UserPrincipal actor) {
        Workplace workplace = findById(id);
        Map<String, Object> before = Map.of(
                "latitude", String.valueOf(workplace.getLatitude()),
                "longitude", String.valueOf(workplace.getLongitude()),
                "radiusMeters", String.valueOf(workplace.getRadiusMeters()));
        workplace.update(request.getName(), request.getAddress(), request.getDetailAddress(), request.getType(),
                request.getLatitude(), request.getLongitude(),
                request.getRadiusMeters(), request.getMaxAccuracyMeters(),
                request.isCheckInAllowed(), request.isCheckOutAllowed(),
                request.getValidFrom(), request.getValidTo());
        auditLogService.record(actor.getId(), actor.getUsername(), "WORKPLACE_UPDATED",
                "WORKPLACE", workplace.getId(),
                Map.of("before", before, "after", Map.of(
                        "latitude", String.valueOf(workplace.getLatitude()),
                        "longitude", String.valueOf(workplace.getLongitude()),
                        "radiusMeters", String.valueOf(workplace.getRadiusMeters()))));
        return WorkplaceResponse.from(workplace);
    }

    @Transactional
    public void deactivateWorkplace(Long id, UserPrincipal actor) {
        findById(id).deactivate();
        auditLogService.record(actor.getId(), actor.getUsername(), "WORKPLACE_DEACTIVATED",
                "WORKPLACE", id, Map.of());
    }

    @Transactional
    public void activateWorkplace(Long id, UserPrincipal actor) {
        findById(id).activate();
        auditLogService.record(actor.getId(), actor.getUsername(), "WORKPLACE_ACTIVATED",
                "WORKPLACE", id, Map.of());
    }

    @Transactional
    public void assignUserToWorkplace(Long userId, Long workplaceId, LocalDate validFrom,
                                       LocalDate validTo, Long assignedBy) {
        if (!workplaceRepository.existsById(workplaceId)) {
            throw new AttendanceException(ErrorCode.WORKPLACE_NOT_FOUND);
        }
        if (!userWorkplaceRepository.existsByUserIdAndWorkplaceId(userId, workplaceId)) {
            UserWorkplace assignment = UserWorkplace.builder()
                    .userId(userId)
                    .workplaceId(workplaceId)
                    .validFrom(validFrom)
                    .validTo(validTo)
                    .assignedBy(assignedBy)
                    .build();
            userWorkplaceRepository.save(assignment);
        }
    }

    @Transactional
    public void assignUserToWorkplace(Long userId, Long workplaceId, LocalDate validFrom,
                                       LocalDate validTo, UserPrincipal actor) {
        assignUserToWorkplace(userId, workplaceId, validFrom, validTo, actor.getId());
        auditLogService.record(actor.getId(), actor.getUsername(), "WORKPLACE_USER_ASSIGNED",
                "WORKPLACE", workplaceId, Map.of("userId", userId));
    }

    @Transactional
    public void assignUsersToWorkplace(Long workplaceId, List<Long> userIds, LocalDate validFrom,
                                        LocalDate validTo, UserPrincipal actor) {
        for (Long userId : userIds) {
            assignUserToWorkplace(userId, workplaceId, validFrom, validTo, actor.getId());
        }
        auditLogService.record(actor.getId(), actor.getUsername(), "WORKPLACE_USERS_BULK_ASSIGNED",
                "WORKPLACE", workplaceId, Map.of("userIds", userIds));
    }

    @Transactional(readOnly = true)
    public List<WorkplaceResponse> getAssignedWorkplaces(Long userId, LocalDate date) {
        return workplaceRepository.findAssignedWorkplacesByUserIdAndDate(userId, date)
                .stream().map(WorkplaceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAssignedUsers(Long workplaceId) {
        return userWorkplaceRepository.findByWorkplaceId(workplaceId).stream()
                .map(uw -> userRepository.findById(uw.getUserId()).orElse(null))
                .filter(u -> u != null)
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkplaceResponse> getWorkplacesForUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new AttendanceException(ErrorCode.USER_NOT_FOUND);
        }
        return userWorkplaceRepository.findByUserId(userId).stream()
                .map(uw -> workplaceRepository.findById(uw.getWorkplaceId()).orElse(null))
                .filter(w -> w != null)
                .map(WorkplaceResponse::from)
                .toList();
    }

    @Transactional
    public void removeUserFromWorkplace(Long workplaceId, Long userId, UserPrincipal actor) {
        userWorkplaceRepository.deleteByUserIdAndWorkplaceId(userId, workplaceId);
        auditLogService.record(actor.getId(), actor.getUsername(), "WORKPLACE_USER_REMOVED",
                "WORKPLACE", workplaceId, Map.of("userId", userId));
    }

    /**
     * 배정을 즉시 삭제하지 않고 validTo를 지정해 종료 예정으로 닫는다. 근무지 변경요청 승인처럼
     * 새 배정이 미래 시점(effectiveDate)부터 시작할 때, 그 전날까지는 기존 근무지가 계속 유효해야
     * 하는 경우에 사용한다(즉시 제거하면 effectiveDate 이전 기간에 배정된 근무지가 없어지는 공백이 생긴다).
     */
    @Transactional
    public void scheduleRemovalFromWorkplace(Long workplaceId, Long userId, LocalDate validTo, UserPrincipal actor) {
        userWorkplaceRepository.findByUserIdAndWorkplaceId(userId, workplaceId)
                .ifPresent(assignment -> assignment.closeOn(validTo));
        auditLogService.record(actor.getId(), actor.getUsername(), "WORKPLACE_USER_REMOVAL_SCHEDULED",
                "WORKPLACE", workplaceId, Map.of("userId", userId, "validTo", validTo.toString()));
    }

    private Workplace findById(Long id) {
        return workplaceRepository.findById(id)
                .orElseThrow(() -> new AttendanceException(ErrorCode.WORKPLACE_NOT_FOUND));
    }
}
