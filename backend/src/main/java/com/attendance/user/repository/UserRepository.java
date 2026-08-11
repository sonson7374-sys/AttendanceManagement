package com.attendance.user.repository;

import com.attendance.user.domain.User;
import com.attendance.user.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByName(String name);
    List<User> findByCompanyId(Long companyId);
    List<User> findByOrganizationIdIn(Collection<Long> organizationIds);
    Page<User> findByIdIn(Collection<Long> ids, Pageable pageable);
    boolean existsByEmail(String email);
    boolean existsByCompanyIdAndEmployeeNumber(Long companyId, String employeeNumber);
    boolean existsByCompanyIdAndEmployeeNumberAndIdNot(Long companyId, String employeeNumber, Long id);
    long countByStatus(UserStatus status);
    List<User> findByStatus(UserStatus status);
}
