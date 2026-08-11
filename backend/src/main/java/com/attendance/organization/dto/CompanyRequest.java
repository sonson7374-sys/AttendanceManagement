package com.attendance.organization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class CompanyRequest {
    @NotBlank(message = "회사명을 입력해주세요.")
    private String name;
    private String businessNumber;
    private String address;
    private String phone;
}
