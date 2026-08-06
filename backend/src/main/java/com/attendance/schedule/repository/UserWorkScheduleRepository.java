package com.attendance.schedule.repository;

import com.attendance.schedule.domain.UserWorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface UserWorkScheduleRepository extends JpaRepository<UserWorkSchedule, Long> {

    @Query("""
        SELECT uws FROM UserWorkSchedule uws
        WHERE uws.userId = :userId
          AND uws.effectiveFrom <= :date
          AND (uws.effectiveUntil IS NULL OR uws.effectiveUntil >= :date)
        ORDER BY uws.effectiveFrom DESC
        LIMIT 1
    """)
    Optional<UserWorkSchedule> findEffectiveSchedule(@Param("userId") Long userId,
                                                      @Param("date") LocalDate date);

    Optional<UserWorkSchedule> findFirstByUserIdAndEffectiveUntilIsNullOrderByEffectiveFromDesc(Long userId);
}
