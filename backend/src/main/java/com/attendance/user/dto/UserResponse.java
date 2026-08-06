package com.attendance.user.dto;

import com.attendance.user.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private String email;
    private String name;
    private String employeeNumber;
    private String phone;
    private Long companyId;
    private Long organizationId;
    private String jobTitle;
    private String employmentType;
    private LocalDate hireDate;
    private LocalDate resignDate;
    private Long defaultWorkplaceId;
    private Long workScheduleId;
    private String role;
    private String level;
    private String status;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .employeeNumber(user.getEmployeeNumber())
                .phone(user.getPhone())
                .companyId(user.getCompanyId())
                .organizationId(user.getOrganizationId())
                .jobTitle(user.getJobTitle())
                .employmentType(user.getEmploymentType())
                .hireDate(user.getHireDate())
                .resignDate(user.getResignDate())
                .defaultWorkplaceId(user.getDefaultWorkplaceId())
                .workScheduleId(user.getWorkScheduleId())
                .role(user.getRole().name())
                .level(user.getLevel())
                .status(user.getStatus().name())
                .build();
    }
}
