package com.attendance.holiday.repository;

import com.attendance.holiday.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    boolean existsByHolidayDate(LocalDate holidayDate);
    Optional<Holiday> findByHolidayDate(LocalDate holidayDate);
    List<Holiday> findAllByOrderByHolidayDateAsc();

    // 지각 판정을 레코드마다 반복하는 화면(월별 조회 등)에서, 그 기간의 공휴일을 한 번에 가져와
    // 메모리(Set)에서 날짜별로 확인하기 위한 배치 조회.
    @Query("SELECT h.holidayDate FROM Holiday h WHERE h.holidayDate BETWEEN :from AND :to")
    List<LocalDate> findHolidayDatesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // 휴일구분 라벨(공휴일/대체공휴일/회사휴일/주말)까지 필요한 화면(MY출근부 등)에서, 날짜별로
    // 반복 조회하지 않고 그 기간의 공휴일 전체를 한 번에 가져오기 위한 배치 조회.
    List<Holiday> findByHolidayDateBetween(LocalDate from, LocalDate to);
}
