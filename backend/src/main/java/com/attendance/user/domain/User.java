package com.attendance.user.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "password")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "employee_number", unique = true, length = 6)
    private String employeeNumber;

    @Column(length = 20)
    private String phone;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "job_title", length = 50)
    private String jobTitle;

    @Column(name = "employment_type", length = 30)
    private String employmentType;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "resign_date")
    private LocalDate resignDate;

    @Column(name = "default_workplace_id")
    private Long defaultWorkplaceId;

    @Column(name = "work_schedule_id")
    private Long workScheduleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false, length = 20)
    private String level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public User(String email, String password, String name, String employeeNumber,
                String phone, Long companyId, Long organizationId, String jobTitle,
                String employmentType, LocalDate hireDate, Long defaultWorkplaceId,
                Long workScheduleId, UserRole role, String level) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.employeeNumber = employeeNumber;
        this.phone = phone;
        this.companyId = companyId;
        this.organizationId = organizationId;
        this.jobTitle = jobTitle;
        this.employmentType = employmentType;
        this.hireDate = hireDate;
        this.defaultWorkplaceId = defaultWorkplaceId;
        this.workScheduleId = workScheduleId;
        this.role = role != null ? role : UserRole.EMPLOYEE;
        this.level = level;
        this.status = UserStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public boolean isLocked() {
        return this.status == UserStatus.LOCKED;
    }

    public void updateProfile(String name, String phone, String jobTitle, String employeeNumber,
                               Long organizationId, String employmentType, LocalDate hireDate, String level) {
        this.name = name;
        this.phone = phone;
        this.jobTitle = jobTitle;
        this.employeeNumber = employeeNumber;
        this.organizationId = organizationId;
        this.employmentType = employmentType;
        this.hireDate = hireDate;
        this.level = level;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.mustChangePassword = false;
    }

    public void resetPasswordByAdmin(String encodedTemporaryPassword) {
        this.password = encodedTemporaryPassword;
        this.mustChangePassword = true;
    }

    public void lock() {
        this.status = UserStatus.LOCKED;
    }

    public void unlock() {
        this.status = UserStatus.ACTIVE;
        this.failedLoginCount = 0;
    }

    /**
     * 로그인 실패 시 호출. maxFailures에 도달하면 계정을 자동 잠금한다.
     * @return 이번 실패로 계정이 잠겼으면 true
     */
    public boolean recordLoginFailure(int maxFailures) {
        this.failedLoginCount++;
        if (this.failedLoginCount >= maxFailures && this.status == UserStatus.ACTIVE) {
            this.status = UserStatus.LOCKED;
            return true;
        }
        return false;
    }

    public void resetLoginFailures() {
        this.failedLoginCount = 0;
    }

    public void deactivate(LocalDate resignDate) {
        this.status = UserStatus.INACTIVE;
        this.resignDate = resignDate;
    }

    public void assignWorkplace(Long workplaceId) {
        this.defaultWorkplaceId = workplaceId;
    }

    public void changeRole(UserRole role) {
        this.role = role;
    }
}
