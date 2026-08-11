package com.attendance.calendar.repository;

import com.attendance.calendar.domain.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    List<CalendarEvent> findByCreatedByOrTargetUserId(Long createdBy, Long targetUserId);

    // 일정관리 화면은 권한레벨과 무관하게 로그인한 계정 본인의 일정(본인이 등록했거나 본인을 대상으로 하는
    // 일정)만 보여준다 — 다른 사람이 등록한 "전체" 공개 일정도 본인이 등록한 것이 아니면 보이지 않는다.
    @Query("SELECT e FROM CalendarEvent e WHERE e.startAt <= :rangeEnd AND e.endAt >= :rangeStart " +
            "AND (e.createdBy = :userId OR e.targetUserId = :userId) ORDER BY e.startAt ASC")
    List<CalendarEvent> findOwnInRange(@Param("rangeStart") Instant rangeStart, @Param("rangeEnd") Instant rangeEnd,
                                        @Param("userId") Long userId);
}
