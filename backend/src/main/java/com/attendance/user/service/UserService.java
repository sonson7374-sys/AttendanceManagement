package com.attendance.user.service;

import com.attendance.attendance.domain.AttendanceChangeRequest;
import com.attendance.attendance.repository.ApprovalHistoryRepository;
import com.attendance.attendance.repository.AttendanceEventRepository;
import com.attendance.attendance.repository.AttendanceRecordRepository;
import com.attendance.attendance.repository.ChangeRequestRepository;
import com.attendance.audit.domain.AuditLog;
import com.attendance.audit.repository.AuditLogRepository;
import com.attendance.audit.service.AuditLogService;
import com.attendance.calendar.repository.CalendarEventRepository;
import com.attendance.commoncode.domain.CommonCode;
import com.attendance.commoncode.repository.CommonCodeRepository;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.leave.domain.LeaveRequest;
import com.attendance.leave.repository.LeaveRequestRepository;
import com.attendance.notification.repository.NotificationRepository;
import com.attendance.outsidework.domain.OutsideWorkRequest;
import com.attendance.outsidework.repository.OutsideWorkRequestRepository;
import com.attendance.schedule.domain.WorkScheduleChangeRequest;
import com.attendance.schedule.repository.UserWorkScheduleRepository;
import com.attendance.schedule.repository.WorkScheduleChangeRequestRepository;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserDevice;
import com.attendance.user.domain.UserRole;
import com.attendance.user.domain.UserStatus;
import com.attendance.user.dto.ChangePasswordRequest;
import com.attendance.user.dto.CreateUserRequest;
import com.attendance.user.dto.PasswordResetResponse;
import com.attendance.user.dto.RegisterDeviceRequest;
import com.attendance.user.dto.UpdateProfileRequest;
import com.attendance.user.dto.UserDeviceResponse;
import com.attendance.user.dto.UserResponse;
import com.attendance.organization.service.OrganizationScopeService;
import com.attendance.user.repository.UserDeviceRepository;
import com.attendance.user.repository.UserRepository;
import com.attendance.workplace.domain.UserWorkplace;
import com.attendance.workplace.domain.WorkplaceChangeRequest;
import com.attendance.workplace.repository.UserWorkplaceRepository;
import com.attendance.workplace.repository.WorkplaceChangeRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String LEVEL_GROUP_CODE = "LEVEL_ROLL";
    // 같은 권한레벨(LEVEL_ROLL) 안에서 직급(jobTitle) 순으로 다시 정렬할 때 쓰는 우선순위 — 목록에 없는
    // 직급(이사·본부장 등 이미 LEVEL_ROLL로 구분되는 직급이나 그 외 자유 입력값)은 맨 뒤로 밀린다.
    private static final List<String> JOB_TITLE_ORDER = List.of("부장", "차장", "과장", "대리", "사원");

    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final OrganizationScopeService organizationScopeService;
    private final CommonCodeRepository commonCodeRepository;
    private final Clock clock;

    // 계정 완전 삭제(deleteUser) 시 본인 이력을 지우고, 남의 신청에 남긴 승인자 참조는 끊기 위해 필요.
    private final AttendanceEventRepository attendanceEventRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final OutsideWorkRequestRepository outsideWorkRequestRepository;
    private final WorkplaceChangeRequestRepository workplaceChangeRequestRepository;
    private final WorkScheduleChangeRequestRepository workScheduleChangeRequestRepository;
    private final NotificationRepository notificationRepository;
    private final UserWorkplaceRepository userWorkplaceRepository;
    private final UserWorkScheduleRepository userWorkScheduleRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * 직원관리 목록. 권한레벨(LEVEL_ROLL) 기준으로 가시성 범위를 적용한다
     * (SYSADMIN/HRADMIN/PRESIDENT 전체, 파트장 이상 레벨은 본인 조직 산하 + 본인, 직원 레벨은 본인만).
     * 부서(organizationId)·이름(name) 검색은 이 가시성 범위 안에서만 적용된다.
     * 목록은 권한레벨(그룹코드 LEVEL_ROLL의 display_order) 순으로 먼저 정렬되고, 같은 권한레벨 안에서는
     * 직급(jobTitle)이 부장 > 차장 > 과장 > 대리 > 사원 순으로, 그 다음은 최근 등록자가 먼저 보인다.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Long actorId, Long organizationId, String name, Pageable pageable) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        List<User> candidates = visibleUserIds == null
                ? userRepository.findAll()
                : userRepository.findAllById(visibleUserIds);

        Map<String, Integer> levelOrder = commonCodeRepository.findByGroupCodeOrderByDisplayOrderAsc(LEVEL_GROUP_CODE)
                .stream().collect(Collectors.toMap(CommonCode::getCode, CommonCode::getDisplayOrder));

        String nameFilter = (name == null || name.isBlank()) ? null : name.trim().toLowerCase();
        List<User> filtered = candidates.stream()
                .filter(u -> organizationId == null || organizationId.equals(u.getOrganizationId()))
                .filter(u -> nameFilter == null || u.getName().toLowerCase().contains(nameFilter))
                .sorted(Comparator
                        .comparing((User u) -> levelOrder.getOrDefault(u.getLevel(), Integer.MAX_VALUE))
                        .thenComparing(u -> jobTitleOrder(u.getJobTitle()))
                        .thenComparing(Comparator.comparing(User::getId).reversed()))
                .toList();

        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<UserResponse> content = filtered.subList(start, end).stream().map(UserResponse::from).toList();
        return new PageImpl<>(content, pageable, filtered.size());
    }

    // jobTitle에는 "부장(수석)", "차장(책임)"처럼 부가 명칭이 붙어 있을 수 있어 접두어로 비교한다.
    private int jobTitleOrder(String jobTitle) {
        if (jobTitle == null) {
            return Integer.MAX_VALUE;
        }
        for (int i = 0; i < JOB_TITLE_ORDER.size(); i++) {
            if (jobTitle.startsWith(JOB_TITLE_ORDER.get(i))) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId, Long actorId) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        if (visibleUserIds != null && !visibleUserIds.contains(userId)) {
            throw new AttendanceException(ErrorCode.ACCESS_DENIED);
        }
        return UserResponse.from(findById(userId));
    }

    /**
     * HR_ADMIN/SYSTEM_ADMIN이 아닌 계정이 프로필 수정·비밀번호 변경을 시도할 때
     * 본인 또는 본인이 관리하는(파트장 이상 레벨 + 조직 산하) 대상인지 검증한다.
     */
    private void checkSelfOrManagedTarget(User actor, Long targetUserId) {
        boolean topAdmin = actor.getRole() == UserRole.HR_ADMIN || actor.getRole() == UserRole.SYSTEM_ADMIN;
        if (topAdmin) {
            return;
        }
        Set<Long> managedUserIds = organizationScopeService.resolveManagedUserIds(actor.getId());
        if (managedUserIds != null && !managedUserIds.contains(targetUserId)) {
            throw new AttendanceException(ErrorCode.ACCESS_DENIED);
        }
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AttendanceException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (request.getEmployeeNumber() != null &&
                userRepository.existsByEmployeeNumber(request.getEmployeeNumber())) {
            throw new AttendanceException(ErrorCode.EMPLOYEE_NUMBER_ALREADY_EXISTS);
        }

        UserRole role = UserRole.EMPLOYEE;
        if (request.getRole() != null) {
            try {
                role = UserRole.valueOf(request.getRole());
            } catch (IllegalArgumentException e) {
                throw new AttendanceException(ErrorCode.INVALID_INPUT, "유효하지 않은 역할입니다: " + request.getRole());
            }
        }
        String level = (request.getLevel() != null && !request.getLevel().isBlank())
                ? request.getLevel() : "EMPLOYEE";

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .employeeNumber(request.getEmployeeNumber())
                .phone(request.getPhone())
                .companyId(request.getCompanyId())
                .organizationId(request.getOrganizationId())
                .jobTitle(request.getJobTitle())
                .employmentType(request.getEmploymentType())
                .hireDate(request.getHireDate())
                .defaultWorkplaceId(request.getDefaultWorkplaceId())
                .workScheduleId(null)
                .role(role)
                .level(level)
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void lockUser(Long userId) {
        findById(userId).lock();
    }

    @Transactional
    public void unlockUser(Long userId) {
        findById(userId).unlock();
    }

    @Transactional
    public void resignUser(Long userId, LocalDate resignDate, Long actorId, String actorEmail) {
        User user = findById(userId);
        user.deactivate(resignDate);
        auditLogService.record(actorId, actorEmail, "USER_RESIGNED", "USER", userId,
                Map.of("resignDate", String.valueOf(resignDate)));
    }

    /**
     * 직원 계정을 DB에서 완전히 삭제한다(퇴사 처리와 달리 되돌릴 수 없다).
     * 이 계정 본인의 출퇴근 기록·신청·기기·알림 등 사용 이력을 먼저 모두 찾아 함께 삭제하고,
     * 이 계정이 "남의 신청을 승인/반려한" 기록처럼 다른 사람 소유의 데이터에 남긴 참조는
     * 그 기록 자체는 지우지 않고 참조만 끊은 뒤(detach) 마지막으로 계정을 삭제한다.
     * 그래도 예상 못한 참조가 남아 삭제가 거부되면 USER_IN_USE로 안내한다.
     */
    @Transactional
    public void deleteUser(Long userId, Long actorId, String actorEmail) {
        if (userId.equals(actorId)) {
            throw new AttendanceException(ErrorCode.CANNOT_DELETE_SELF);
        }
        User user = findById(userId);
        if (user.getStatus() != UserStatus.INACTIVE) {
            throw new AttendanceException(ErrorCode.USER_NOT_RESIGNED);
        }
        String email = user.getEmail();

        purgeOwnHistory(userId);
        detachFromOthersHistory(userId);

        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new AttendanceException(ErrorCode.USER_IN_USE);
        }
        auditLogService.record(actorId, actorEmail, "USER_DELETED", "USER", userId, Map.of("email", email));
    }

    /** 이 계정 본인이 남긴 사용 이력(출퇴근·신청·기기·알림·일정 등)을 모두 삭제한다. */
    private void purgeOwnHistory(Long userId) {
        List<AttendanceChangeRequest> ownChangeRequests = changeRequestRepository.findByRequesterIdOrderByCreatedAtDesc(userId);
        approvalHistoryRepository.deleteAll(approvalHistoryRepository.findByRequestIdInAndRequestType(
                ownChangeRequests.stream().map(AttendanceChangeRequest::getId).toList(), "CHANGE_REQUEST"));
        changeRequestRepository.deleteAll(ownChangeRequests);

        List<LeaveRequest> ownLeaveRequests = leaveRequestRepository.findByRequesterIdOrderByCreatedAtDesc(userId);
        approvalHistoryRepository.deleteAll(approvalHistoryRepository.findByRequestIdInAndRequestType(
                ownLeaveRequests.stream().map(LeaveRequest::getId).toList(), "LEAVE_REQUEST"));
        leaveRequestRepository.deleteAll(ownLeaveRequests);

        List<OutsideWorkRequest> ownOutsideWorkRequests = outsideWorkRequestRepository.findByRequesterIdOrderByCreatedAtDesc(userId);
        approvalHistoryRepository.deleteAll(approvalHistoryRepository.findByRequestIdInAndRequestType(
                ownOutsideWorkRequests.stream().map(OutsideWorkRequest::getId).toList(), "OUTSIDE_WORK_REQUEST"));
        outsideWorkRequestRepository.deleteAll(ownOutsideWorkRequests);

        List<WorkplaceChangeRequest> ownWorkplaceChangeRequests = workplaceChangeRequestRepository.findByRequesterIdOrderByCreatedAtDesc(userId);
        approvalHistoryRepository.deleteAll(approvalHistoryRepository.findByRequestIdInAndRequestType(
                ownWorkplaceChangeRequests.stream().map(WorkplaceChangeRequest::getId).toList(), "WORKPLACE_CHANGE_REQUEST"));
        workplaceChangeRequestRepository.deleteAll(ownWorkplaceChangeRequests);

        List<WorkScheduleChangeRequest> ownWorkScheduleChangeRequests = workScheduleChangeRequestRepository.findByRequesterIdOrderByCreatedAtDesc(userId);
        approvalHistoryRepository.deleteAll(approvalHistoryRepository.findByRequestIdInAndRequestType(
                ownWorkScheduleChangeRequests.stream().map(WorkScheduleChangeRequest::getId).toList(), "WORK_SCHEDULE_CHANGE_REQUEST"));
        workScheduleChangeRequestRepository.deleteAll(ownWorkScheduleChangeRequests);

        attendanceEventRepository.deleteAll(attendanceEventRepository.findByUserId(userId));
        attendanceRecordRepository.deleteAll(attendanceRecordRepository.findByUserId(userId));
        notificationRepository.deleteAll(notificationRepository.findByUserId(userId));
        userDeviceRepository.deleteAll(userDeviceRepository.findByUserIdOrderByLastSeenAtDesc(userId));
        userWorkplaceRepository.deleteAll(userWorkplaceRepository.findByUserId(userId));
        userWorkScheduleRepository.deleteAll(userWorkScheduleRepository.findByUserId(userId));
        calendarEventRepository.deleteAll(calendarEventRepository.findByCreatedByOrTargetUserId(userId, userId));
    }

    /**
     * 이 계정이 남(다른 직원)의 신청·배정·감사로그에 승인자/배정자/수행자로 남긴 참조를 끊는다.
     * 그 신청·배정·로그 자체는 다른 사람의 정당한 이력이므로 지우지 않고 참조만 null로 바꾼다.
     */
    private void detachFromOthersHistory(Long userId) {
        List<AttendanceChangeRequest> approvedChangeRequests = changeRequestRepository.findByCurrentApproverId(userId);
        approvedChangeRequests.forEach(AttendanceChangeRequest::detachApprover);
        changeRequestRepository.saveAll(approvedChangeRequests);

        List<LeaveRequest> approvedLeaveRequests = leaveRequestRepository.findByCurrentApproverId(userId);
        approvedLeaveRequests.forEach(LeaveRequest::detachApprover);
        leaveRequestRepository.saveAll(approvedLeaveRequests);

        List<OutsideWorkRequest> approvedOutsideWorkRequests = outsideWorkRequestRepository.findByCurrentApproverId(userId);
        approvedOutsideWorkRequests.forEach(OutsideWorkRequest::detachApprover);
        outsideWorkRequestRepository.saveAll(approvedOutsideWorkRequests);

        List<WorkplaceChangeRequest> approvedWorkplaceChangeRequests = workplaceChangeRequestRepository.findByCurrentApproverId(userId);
        approvedWorkplaceChangeRequests.forEach(WorkplaceChangeRequest::detachApprover);
        workplaceChangeRequestRepository.saveAll(approvedWorkplaceChangeRequests);

        List<WorkScheduleChangeRequest> approvedWorkScheduleChangeRequests = workScheduleChangeRequestRepository.findByCurrentApproverId(userId);
        approvedWorkScheduleChangeRequests.forEach(WorkScheduleChangeRequest::detachApprover);
        workScheduleChangeRequestRepository.saveAll(approvedWorkScheduleChangeRequests);

        // approval_histories.approver_id는 NOT NULL이라 detach가 불가능해, 남의 신청에 남긴 승인 이력이라도
        // 삭제할 수밖에 없다(그 신청 자체와 current_approver_id는 위에서 이미 detach해 보존했다).
        approvalHistoryRepository.deleteAll(approvalHistoryRepository.findByApproverId(userId));

        List<AuditLog> actedLogs = auditLogRepository.findByActorId(userId);
        actedLogs.forEach(AuditLog::detachActor);
        auditLogRepository.saveAll(actedLogs);

        List<UserWorkplace> assignedByThisUser = userWorkplaceRepository.findByAssignedBy(userId);
        assignedByThisUser.forEach(UserWorkplace::detachAssigner);
        userWorkplaceRepository.saveAll(assignedByThisUser);
    }

    @Transactional
    public PasswordResetResponse resetPasswordByAdmin(Long userId, Long actorId, String actorEmail) {
        User user = findById(userId);
        String temporaryPassword = UUID.randomUUID().toString().substring(0, 12);
        user.resetPasswordByAdmin(passwordEncoder.encode(temporaryPassword));
        auditLogService.record(actorId, actorEmail, "USER_PASSWORD_RESET_BY_ADMIN", "USER", userId, Map.of());
        return PasswordResetResponse.builder()
                .userId(userId)
                .temporaryPassword(temporaryPassword)
                .build();
    }

    /**
     * 관리자(HR_ADMIN/SYSTEM_ADMIN) 또는 본인·파트장 이상이 관리하는 하위 직원에 대해
     * 다른 직원의 비밀번호를 직접 지정한다(임시 비밀번호 생성 없이).
     * 이미 값을 알고 지정하는 것이므로, 임시 비밀번호 발급과 마찬가지로 다음 로그인 시 비밀번호를 다시 바꾸도록 강제한다.
     */
    @Transactional
    public void setPasswordByAdmin(Long userId, String newPassword, Long actorId, String actorEmail) {
        User actor = findById(actorId);
        checkSelfOrManagedTarget(actor, userId);
        User user = findById(userId);
        user.resetPasswordByAdmin(passwordEncoder.encode(newPassword));
        auditLogService.record(actorId, actorEmail, "USER_PASSWORD_SET_BY_ADMIN", "USER", userId, Map.of());
    }

    @Transactional(readOnly = true)
    public List<UserDeviceResponse> listDevices(Long userId) {
        return listMyDevices(userId);
    }

    /**
     * HR_ADMIN/SYSTEM_ADMIN이 아닌 계정(본인 또는 본인이 관리하는 하위 직원 대상)은
     * 권한레벨(level)을 직접 바꿀 수 없다 — 그대로 허용하면 자기 레벨을 임의로 올려
     * 파트장 이상 권한을 스스로 부여하는 권한 상승이 가능해지기 때문에, 요청값을 무시하고
     * 기존 레벨을 그대로 유지한다.
     */
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request, Long actorId) {
        User actor = findById(actorId);
        checkSelfOrManagedTarget(actor, userId);
        boolean topAdmin = actor.getRole() == UserRole.HR_ADMIN || actor.getRole() == UserRole.SYSTEM_ADMIN;

        User user = findById(userId);
        if (request.getEmployeeNumber() != null &&
                userRepository.existsByEmployeeNumberAndIdNot(request.getEmployeeNumber(), userId)) {
            throw new AttendanceException(ErrorCode.EMPLOYEE_NUMBER_ALREADY_EXISTS);
        }
        String levelToApply = topAdmin ? request.getLevel() : user.getLevel();
        user.updateProfile(request.getName(), request.getPhone(), request.getJobTitle(),
                request.getEmployeeNumber(), request.getOrganizationId(),
                request.getEmploymentType(), request.getHireDate(), levelToApply);
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findById(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AttendanceException(ErrorCode.INVALID_CREDENTIALS);
        }
        user.changePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    @Transactional
    public UserResponse changeRole(Long userId, String roleName) {
        User user = findById(userId);
        UserRole role;
        try {
            role = UserRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "유효하지 않은 역할입니다: " + roleName);
        }
        user.changeRole(role);
        return UserResponse.from(user);
    }

    @Transactional
    public UserDeviceResponse registerDevice(Long userId, RegisterDeviceRequest request) {
        findById(userId);
        Instant now = Instant.now(clock);
        UserDevice device = userDeviceRepository.findByUserIdAndDeviceId(userId, request.getDeviceId())
                .map(existing -> {
                    existing.touch(now, request.getFcmToken());
                    return existing;
                })
                .orElseGet(() -> userDeviceRepository.save(UserDevice.builder()
                        .userId(userId)
                        .deviceId(request.getDeviceId())
                        .devicePlatform(request.getDevicePlatform())
                        .deviceName(request.getDeviceName())
                        .fcmToken(request.getFcmToken())
                        .registeredAt(now)
                        .build()));
        return UserDeviceResponse.from(device);
    }

    @Transactional(readOnly = true)
    public List<UserDeviceResponse> listMyDevices(Long userId) {
        return userDeviceRepository.findByUserIdOrderByLastSeenAtDesc(userId).stream()
                .map(UserDeviceResponse::from)
                .toList();
    }

    @Transactional
    public void revokeMyDevice(Long userId, String deviceId) {
        UserDevice device = userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND));
        device.revoke();
    }

    @Transactional
    public void revokeDeviceByAdmin(Long actorId, String actorEmail, Long targetUserId, String deviceId) {
        User target = findById(targetUserId);
        UserDevice device = userDeviceRepository.findByUserIdAndDeviceId(targetUserId, deviceId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND));
        device.revoke();
        auditLogService.record(actorId, actorEmail, "USER_DEVICE_REVOKED", "USER", targetUserId,
                Map.of("deviceId", deviceId, "targetUserName", target.getName()));
    }

    private User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.USER_NOT_FOUND));
    }
}
