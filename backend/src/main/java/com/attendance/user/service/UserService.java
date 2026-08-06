package com.attendance.user.service;

import com.attendance.audit.service.AuditLogService;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserDevice;
import com.attendance.user.domain.UserRole;
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
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final OrganizationScopeService organizationScopeService;
    private final Clock clock;

    /**
     * 직원관리 목록. 권한레벨(LEVEL_ROLL) 기준으로 가시성 범위를 적용한다
     * (SYSADMIN/HRADMIN/PRESIDENT 전체, 파트장 이상 레벨은 본인 조직 산하 + 본인, 직원 레벨은 본인만).
     * 부서(organizationId)·이름(name) 검색은 이 가시성 범위 안에서만 적용된다.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Long actorId, Long organizationId, String name, Pageable pageable) {
        Set<Long> visibleUserIds = organizationScopeService.resolveVisibleUserIdsByLevel(actorId);
        List<User> candidates = visibleUserIds == null
                ? userRepository.findAll()
                : userRepository.findAllById(visibleUserIds);

        String nameFilter = (name == null || name.isBlank()) ? null : name.trim().toLowerCase();
        List<User> filtered = candidates.stream()
                .filter(u -> organizationId == null || organizationId.equals(u.getOrganizationId()))
                .filter(u -> nameFilter == null || u.getName().toLowerCase().contains(nameFilter))
                .sorted(Comparator.comparing(User::getId).reversed())
                .toList();

        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<UserResponse> content = filtered.subList(start, end).stream().map(UserResponse::from).toList();
        return new PageImpl<>(content, pageable, filtered.size());
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
