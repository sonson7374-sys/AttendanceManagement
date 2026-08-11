package com.attendance.organization.dto;

import com.attendance.organization.domain.Company;

import java.time.Instant;

public record CompanyCreateResponse(
        Long id,
        String name,
        String businessNumber,
        String address,
        String phone,
        boolean active,
        Instant createdAt,
        String adminEmail,
        String temporaryPassword
) {
    public static CompanyCreateResponse of(Company company, String adminEmail, String temporaryPassword) {
        return new CompanyCreateResponse(
                company.getId(), company.getName(), company.getBusinessNumber(),
                company.getAddress(), company.getPhone(), company.isActive(), company.getCreatedAt(),
                adminEmail, temporaryPassword
        );
    }
}
