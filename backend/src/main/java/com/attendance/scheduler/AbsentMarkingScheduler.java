package com.attendance.scheduler;

import com.attendance.attendance.domain.AttendanceRecord;
import com.attendance.attendance.domain.AttendanceStatus;
import com.attendance.attendance.repository.AttendanceRecordRepository;
import com.attendance.common.config.AppConfig;
import com.attendance.holiday.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AbsentMarkingScheduler {

    private final AttendanceRecordRepository recordRepository;
    private final HolidayRepository holidayRepository;
    private final Clock clock;

    // 매일 자정 이후 00:05 KST (= 전일 15:05 UTC) 에 전날 결근 처리
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void markAbsentUsers() {
        LocalDate yesterday = Instant.now(clock).atZone(AppConfig.SEOUL).toLocalDate().minusDays(1);

        if (yesterday.getDayOfWeek() == DayOfWeek.SATURDAY || yesterday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            log.info("주말이므로 결근 배치를 건너뜁니다: targetDate={}", yesterday);
            return;
        }

        if (holidayRepository.existsByHolidayDate(yesterday)) {
            log.info("공휴일이므로 결근 배치를 건너뜁니다: targetDate={}", yesterday);
            return;
        }

        log.info("결근 배치 시작: targetDate={}", yesterday);

        List<Long> absentUserIds = recordRepository.findActiveUserIdsWithoutAttendanceOnDate(yesterday);
        if (absentUserIds.isEmpty()) {
            log.info("결근자 없음: targetDate={}", yesterday);
            return;
        }

        int count = 0;
        for (Long userId : absentUserIds) {
            recordRepository.save(AttendanceRecord.createAbsent(userId, yesterday));
            count++;
        }

        log.info("결근 처리 완료: targetDate={} count={}", yesterday, count);
    }
}
