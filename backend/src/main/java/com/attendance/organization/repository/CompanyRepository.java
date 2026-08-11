package com.attendance.organization.repository;

import com.attendance.organization.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    List<Company> findByActive(boolean active);
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
}
