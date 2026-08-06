package com.attendance.schedule.repository;

import com.attendance.schedule.domain.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {
    Optional<WorkSchedule> findByCompanyIdAndDefaultScheduleTrue(Long companyId);
}
