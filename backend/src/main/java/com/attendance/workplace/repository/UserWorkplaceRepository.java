package com.attendance.workplace.repository;

import com.attendance.workplace.domain.UserWorkplace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserWorkplaceRepository extends JpaRepository<UserWorkplace, Long> {
    List<UserWorkplace> findByUserId(Long userId);
    List<UserWorkplace> findByWorkplaceId(Long workplaceId);
    Optional<UserWorkplace> findByUserIdAndWorkplaceId(Long userId, Long workplaceId);
    boolean existsByUserIdAndWorkplaceId(Long userId, Long workplaceId);
    void deleteByUserIdAndWorkplaceId(Long userId, Long workplaceId);
    List<UserWorkplace> findByUserIdIn(Collection<Long> userIds);
}
