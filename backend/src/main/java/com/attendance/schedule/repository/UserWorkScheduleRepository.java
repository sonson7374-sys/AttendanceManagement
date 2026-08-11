package com.attendance.schedule.repository;

import com.attendance.schedule.domain.UserWorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserWorkScheduleRepository extends JpaRepository<UserWorkSchedule, Long> {

    List<UserWorkSchedule> findByUserId(Long userId);

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

    // 여러 사용자의 특정 날짜 기준 근무제 배정을 한 번에 조회하기 위한 배치 버전(findEffectiveSchedule 참고).
    // 사용자별 "가장 최근에 시작된 유효 배정"은 호출부에서 effectiveFrom 기준으로 골라 쓴다.
    @Query("""
        SELECT uws FROM UserWorkSchedule uws
        WHERE uws.userId IN :userIds
          AND uws.effectiveFrom <= :date
          AND (uws.effectiveUntil IS NULL OR uws.effectiveUntil >= :date)
    """)
    List<UserWorkSchedule> findEffectiveSchedules(@Param("userIds") List<Long> userIds,
                                                   @Param("date") LocalDate date);

    // 월별 조회처럼 여러 날짜(레코드)에 걸쳐 근무제를 매칭해야 할 때, 그 기간과 유효기간이 겹치는
    // 배정을 전부 한 번에 가져온다 — 월 중간에 근무제가 바뀐 경우도 놓치지 않는다.
    // (특정 날짜에 어떤 배정이 적용되는지는 호출부에서 각 레코드의 workDate로 다시 골라 쓴다.)
    @Query("""
        SELECT uws FROM UserWorkSchedule uws
        WHERE uws.userId IN :userIds
          AND uws.effectiveFrom <= :to
          AND (uws.effectiveUntil IS NULL OR uws.effectiveUntil >= :from)
    """)
    List<UserWorkSchedule> findOverlapping(@Param("userIds") List<Long> userIds,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);
}
