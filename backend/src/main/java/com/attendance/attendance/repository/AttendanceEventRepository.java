package com.attendance.attendance.repository;

import com.attendance.attendance.domain.AttendanceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, Long> {
    List<AttendanceEvent> findByRecordId(Long recordId);
    List<AttendanceEvent> findByUserId(Long userId);
    Optional<AttendanceEvent> findFirstByUserIdOrderByEventAtDesc(Long userId);

    @Modifying
    @Query("DELETE FROM AttendanceEvent e WHERE e.eventAt < :cutoff")
    int deleteByEventAtBefore(@Param("cutoff") Instant cutoff);
}
