package com.attendance.organization.service;

import com.attendance.audit.service.AuditLogService;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.organization.domain.Company;
import com.attendance.organization.domain.Organization;
import com.attendance.organization.dto.CompanyCreateRequest;
import com.attendance.organization.dto.CompanyCreateResponse;
import com.attendance.organization.dto.CompanyRequest;
import com.attendance.organization.dto.CompanyResponse;
import com.attendance.organization.repository.CompanyRepository;
import com.attendance.organization.repository.OrganizationRepository;
import com.attendance.schedule.domain.WorkSchedule;
import com.attendance.schedule.repository.WorkScheduleRepository;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserPrincipal;
import com.attendance.user.domain.UserRole;
import com.attendance.user.repository.UserRepository;
import com.attendance.workplace.domain.Workplace;
import com.attendance.workplace.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 새 회사를 회사 정보만이 아니라 그 회사에 바로 로그인해서 쓸 수 있는 상태(기본 조직·근무지·근무제·
 * 최초 SYSTEM_ADMIN 계정)까지 한 번에 만든다 — V030 마이그레이션이 SQL로 했던 회사2 부트스트랩과
 * 동일한 절차를 애플리케이션 코드로 재구현한 것이다.
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private static final String DEFAULT_ORGANIZATION_NAME = "본사";
    private static final String DEFAULT_WORKPLACE_NAME = "본사(임시)";
    private static final BigDecimal DEFAULT_LATITUDE = BigDecimal.valueOf(37.5663);
    private static final BigDecimal DEFAULT_LONGITUDE = BigDecimal.valueOf(126.9779);
    private static final int DEFAULT_RADIUS_METERS = 500;
    private static final String DEFAULT_WORK_SCHEDULE_NAME = "기본 근무제";
    private static final String DEFAULT_ADMIN_EMPLOYEE_NUMBER = "SYS001";
    private static final String DEFAULT_ADMIN_LEVEL = "SYSADMIN";

    private final CompanyRepository companyRepository;
    private final OrganizationRepository organizationRepository;
    private final WorkplaceRepository workplaceRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<CompanyResponse> listCompanies() {
        return companyRepository.findAll().stream().map(CompanyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse getMyCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .map(CompanyResponse::from)
                .orElseThrow(() -> new AttendanceException(ErrorCode.COMPANY_NOT_FOUND));
    }

    @Transactional
    public CompanyResponse updateCompany(Long id, CompanyRequest request, UserPrincipal actor) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new AttendanceException(ErrorCode.COMPANY_NOT_FOUND));
        if (companyRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new AttendanceException(ErrorCode.COMPANY_NAME_ALREADY_EXISTS);
        }
        String beforeName = company.getName();
        company.update(request.getName(), request.getBusinessNumber(), request.getAddress(), request.getPhone());
        auditLogService.record(actor.getId(), actor.getUsername(), "COMPANY_UPDATED",
                "COMPANY", company.getId(), Map.of("before", beforeName, "after", company.getName()));
        return CompanyResponse.from(company);
    }

    @Transactional
    public CompanyCreateResponse createCompany(CompanyCreateRequest request, UserPrincipal actor) {
        if (companyRepository.existsByName(request.getName())) {
            throw new AttendanceException(ErrorCode.COMPANY_NAME_ALREADY_EXISTS);
        }
        if (userRepository.existsByEmail(request.getAdminEmail())) {
            throw new AttendanceException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Company company = companyRepository.save(Company.builder()
                .name(request.getName())
                .businessNumber(request.getBusinessNumber())
                .address(request.getAddress())
                .phone(request.getPhone())
                .build());

        Organization rootOrganization = organizationRepository.save(Organization.builder()
                .companyId(company.getId())
                .name(DEFAULT_ORGANIZATION_NAME)
                .build());

        workplaceRepository.save(Workplace.builder()
                .companyId(company.getId())
                .name(DEFAULT_WORKPLACE_NAME)
                .latitude(DEFAULT_LATITUDE)
                .longitude(DEFAULT_LONGITUDE)
                .radiusMeters(DEFAULT_RADIUS_METERS)
                .build());

        workScheduleRepository.save(WorkSchedule.builder()
                .companyId(company.getId())
                .name(DEFAULT_WORK_SCHEDULE_NAME)
                .workStartTime(LocalTime.of(9, 0))
                .workEndTime(LocalTime.of(18, 0))
                .requiredWorkMinutes(480)
                .overtimeThresholdMin(480)
                .breakMinutes(60)
                .defaultSchedule(true)
                .build());

        String temporaryPassword = UUID.randomUUID().toString().substring(0, 12);
        String adminEmployeeNumber = request.getAdminEmployeeNumber() != null && !request.getAdminEmployeeNumber().isBlank()
                ? request.getAdminEmployeeNumber() : DEFAULT_ADMIN_EMPLOYEE_NUMBER;
        User admin = User.builder()
                .email(request.getAdminEmail())
                .password(passwordEncoder.encode(temporaryPassword))
                .name(request.getAdminName())
                .employeeNumber(adminEmployeeNumber)
                .companyId(company.getId())
                .organizationId(rootOrganization.getId())
                .role(UserRole.SYSTEM_ADMIN)
                .level(DEFAULT_ADMIN_LEVEL)
                .build();
        admin.resetPasswordByAdmin(passwordEncoder.encode(temporaryPassword));
        userRepository.save(admin);

        auditLogService.record(actor.getId(), actor.getUsername(), "COMPANY_CREATED",
                "COMPANY", company.getId(), Map.of("name", company.getName(), "adminEmail", request.getAdminEmail()));

        return CompanyCreateResponse.of(company, request.getAdminEmail(), temporaryPassword);
    }
}
