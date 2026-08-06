package com.attendance.attendance.repository;

import com.attendance.attendance.domain.BreakRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BreakRecordRepository extends JpaRepository<BreakRecord, Long> {
    List<BreakRecord> findByRecordId(Long recordId);
    Optional<BreakRecord> findByRecordIdAndEndAtIsNull(Long recordId);
}
