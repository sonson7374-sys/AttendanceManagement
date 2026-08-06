package com.attendance.scheduler;

import com.attendance.attendance.repository.AttendanceRecordRepository;
import com.attendance.holiday.repository.HolidayRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AbsentMarkingSchedulerTest {

    @Mock AttendanceRecordRepository recordRepository;
    @Mock HolidayRepository holidayRepository;

    private AbsentMarkingScheduler schedulerAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new AbsentMarkingScheduler(recordRepository, holidayRepository, clock);
    }

    @Test
    @DisplayName("전날이 토요일이면 결근 배치를 건너뛴다")
    void skipsWhenYesterdayWasSaturday() {
        // clock=일요일 00:00 UTC(=09:00 KST) -> yesterday=토요일
        AbsentMarkingScheduler scheduler = schedulerAt("2026-08-02T00:00:00Z");

        scheduler.markAbsentUsers();

        verify(recordRepository, never()).findActiveUserIdsWithoutAttendanceOnDate(any());
        verify(holidayRepository, never()).existsByHolidayDate(any());
    }

    @Test
    @DisplayName("전날이 일요일이면 결근 배치를 건너뛴다")
    void skipsWhenYesterdayWasSunday() {
        // clock=월요일 00:00 UTC(=09:00 KST) -> yesterday=일요일
        AbsentMarkingScheduler scheduler = schedulerAt("2026-08-03T00:00:00Z");

        scheduler.markAbsentUsers();

        verify(recordRepository, never()).findActiveUserIdsWithoutAttendanceOnDate(any());
        verify(holidayRepository, never()).existsByHolidayDate(any());
    }

    @Test
    @DisplayName("평일이고 공휴일이 아니면 결근 배치를 실행한다")
    void runsOnRegularWeekday() {
        // clock=화요일 00:00 UTC(=09:00 KST) -> yesterday=월요일
        AbsentMarkingScheduler scheduler = schedulerAt("2026-08-04T00:00:00Z");
        given(holidayRepository.existsByHolidayDate(any())).willReturn(false);
        given(recordRepository.findActiveUserIdsWithoutAttendanceOnDate(any())).willReturn(java.util.List.of());

        scheduler.markAbsentUsers();

        verify(recordRepository).findActiveUserIdsWithoutAttendanceOnDate(any());
    }
}
