package com.attendance.organization.repository;

import com.attendance.organization.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    List<Organization> findByCompanyIdAndActive(Long companyId, boolean active);
    List<Organization> findByParentId(Long parentId);
    Optional<Organization> findByCompanyIdAndName(Long companyId, String name);
}
