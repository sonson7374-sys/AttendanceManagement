package com.attendance.organization.dto;

import com.attendance.organization.domain.Organization;

import java.time.Instant;

public record OrganizationResponse(
        Long id,
        Long companyId,
        Long parentId,
        String name,
        Integer displayOrder,
        boolean active,
        Instant createdAt
) {
    public static OrganizationResponse from(Organization org) {
        return new OrganizationResponse(
                org.getId(), org.getCompanyId(), org.getParentId(),
                org.getName(), org.getDisplayOrder(), org.isActive(), org.getCreatedAt()
        );
    }
}
