package com.attendance.attendance.repository;

import com.attendance.attendance.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByUserIdAndWorkDate(Long userId, LocalDate workDate);

    List<AttendanceRecord> findByUserId(Long userId);

    boolean existsByUserIdAndWorkDate(Long userId, LocalDate workDate);

    List<AttendanceRecord> findByUserIdAndWorkDateBetween(Long userId, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByWorkDate(LocalDate workDate);

    @Query("SELECT r FROM AttendanceRecord r WHERE r.workDate BETWEEN :from AND :to")
    List<AttendanceRecord> findByWorkDateBetweenAllUsers(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT u.id FROM User u
            WHERE u.status = com.attendance.user.domain.UserStatus.ACTIVE
              AND u.id NOT IN (
                SELECT r.userId FROM AttendanceRecord r WHERE r.workDate = :date
              )
            """)
    List<Long> findActiveUserIdsWithoutAttendanceOnDate(@Param("date") LocalDate date);
}
