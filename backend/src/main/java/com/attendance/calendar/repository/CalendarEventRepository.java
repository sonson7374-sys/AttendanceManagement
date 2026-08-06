package com.attendance.calendar.repository;

import com.attendance.calendar.domain.CalendarEvent;
import com.attendance.calendar.domain.CalendarEventVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    @Query("SELECT e FROM CalendarEvent e WHERE e.startAt <= :rangeEnd AND e.endAt >= :rangeStart ORDER BY e.startAt ASC")
    List<CalendarEvent> findAllInRange(@Param("rangeStart") Instant rangeStart, @Param("rangeEnd") Instant rangeEnd);

    @Query("SELECT e FROM CalendarEvent e WHERE e.startAt <= :rangeEnd AND e.endAt >= :rangeStart " +
            "AND (e.visibility = :allVisibility OR e.targetUserId = :userId) ORDER BY e.startAt ASC")
    List<CalendarEvent> findVisibleInRange(@Param("rangeStart") Instant rangeStart, @Param("rangeEnd") Instant rangeEnd,
                                            @Param("userId") Long userId,
                                            @Param("allVisibility") CalendarEventVisibility allVisibility);
}
