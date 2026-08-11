package com.attendance.workplace.repository;

import com.attendance.workplace.domain.UserWorkplace;
import com.attendance.workplace.domain.Workplace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkplaceRepository extends JpaRepository<Workplace, Long> {

    List<Workplace> findByCompanyIdAndActive(Long companyId, boolean active);

    List<Workplace> findByCompanyId(Long companyId);

    Optional<Workplace> findByCompanyIdAndName(Long companyId, String name);

    @Query("""
        SELECT w FROM Workplace w
        JOIN UserWorkplace uw ON uw.workplaceId = w.id
        WHERE uw.userId = :userId
          AND w.active = true
          AND (:date >= w.validFrom OR w.validFrom IS NULL)
          AND (:date <= w.validTo OR w.validTo IS NULL)
          AND (:date >= uw.validFrom OR uw.validFrom IS NULL)
          AND (:date <= uw.validTo OR uw.validTo IS NULL)
        """)
    List<Workplace> findAssignedWorkplacesByUserIdAndDate(
            @Param("userId") Long userId,
            @Param("date") LocalDate date);

    // findAssignedWorkplacesByUserIdAndDate의 배치 버전. userId를 같이 반환해야 직원별로 다시 묶을 수 있어
    // Object[]{userId, workplace}로 받는다(호출부에서 userId별로 그룹핑).
    @Query("""
        SELECT uw.userId, w FROM Workplace w
        JOIN UserWorkplace uw ON uw.workplaceId = w.id
        WHERE uw.userId IN :userIds
          AND w.active = true
          AND (:date >= w.validFrom OR w.validFrom IS NULL)
          AND (:date <= w.validTo OR w.validTo IS NULL)
          AND (:date >= uw.validFrom OR uw.validFrom IS NULL)
          AND (:date <= uw.validTo OR uw.validTo IS NULL)
        """)
    List<Object[]> findAssignedWorkplacesByUserIdsAndDate(
            @Param("userIds") List<Long> userIds,
            @Param("date") LocalDate date);
}
