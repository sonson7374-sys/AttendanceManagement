package com.attendance.workplace.repository;

import com.attendance.workplace.domain.UserWorkplace;
import com.attendance.workplace.domain.Workplace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WorkplaceRepository extends JpaRepository<Workplace, Long> {

    List<Workplace> findByCompanyIdAndActive(Long companyId, boolean active);

    List<Workplace> findByCompanyId(Long companyId);

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
}
