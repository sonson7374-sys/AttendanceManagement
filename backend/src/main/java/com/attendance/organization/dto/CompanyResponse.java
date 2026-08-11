package com.attendance.organization.dto;

import com.attendance.organization.domain.Company;

import java.time.Instant;

public record CompanyResponse(
        Long id,
        String name,
        String businessNumber,
        String address,
        String phone,
        boolean active,
        Instant createdAt
) {
    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
                company.getId(), company.getName(), company.getBusinessNumber(),
                company.getAddress(), company.getPhone(), company.isActive(), company.getCreatedAt()
        );
    }
}
