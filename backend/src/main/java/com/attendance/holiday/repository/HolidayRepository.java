package com.attendance.holiday.repository;

import com.attendance.holiday.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    boolean existsByHolidayDate(LocalDate holidayDate);
    Optional<Holiday> findByHolidayDate(LocalDate holidayDate);
    List<Holiday> findAllByOrderByHolidayDateAsc();
}
