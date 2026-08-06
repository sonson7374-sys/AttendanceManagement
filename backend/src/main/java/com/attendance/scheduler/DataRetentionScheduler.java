package com.attendance.scheduler;

import com.attendance.attendance.repository.AttendanceEventRepository;
import com.attendance.audit.repository.AuditLogRepository;
import com.attendance.config.PrivacyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 위치정보(원본 출퇴근 이벤트)와 감사 로그를 보관기간 경과 후 정리한다.
 * CLAUDE.md 보안·개인정보 규칙: "보관기간 경과 후 삭제 또는 비식별화".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionScheduler {

    private final AttendanceEventRepository attendanceEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final PrivacyProperties privacyProperties;
    private final Clock clock;

    // 매일 새벽 03:00 KST 실행
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeExpiredData() {
        Instant now = Instant.now(clock);

        Instant locationCutoff = now.minus(privacyProperties.getLocationRetentionDays(), ChronoUnit.DAYS);
        int deletedEvents = attendanceEventRepository.deleteByEventAtBefore(locationCutoff);
        log.info("위치정보 보관기간 경과 데이터 삭제: cutoff={} count={}", locationCutoff, deletedEvents);

        Instant auditCutoff = now.minus(privacyProperties.getAuditLogRetentionDays(), ChronoUnit.DAYS);
        int deletedAuditLogs = auditLogRepository.deleteByCreatedAtBefore(auditCutoff);
        log.info("감사 로그 보관기간 경과 데이터 삭제: cutoff={} count={}", auditCutoff, deletedAuditLogs);
    }
}
