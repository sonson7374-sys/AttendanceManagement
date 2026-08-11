package com.attendance.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class CompanyCreateRequest {
    @NotBlank(message = "회사명을 입력해주세요.")
    private String name;
    private String businessNumber;
    private String address;
    private String phone;

    @NotBlank(message = "최초 관리자 이메일을 입력해주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String adminEmail;

    @NotBlank(message = "최초 관리자 이름을 입력해주세요.")
    private String adminName;

    private String adminEmployeeNumber;
}
