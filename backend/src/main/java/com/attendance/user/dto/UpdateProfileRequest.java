package com.attendance.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class UpdateProfileRequest {
    @NotBlank
    private String name;
    private String phone;
    private String jobTitle;
    private String employeeNumber;
    private Long organizationId;
    private String employmentType;
    private LocalDate hireDate;

    @NotBlank(message = "권한레벨을 선택해주세요.")
    @Size(max = 20, message = "권한레벨은 20자를 초과할 수 없습니다.")
    private String level;
}
