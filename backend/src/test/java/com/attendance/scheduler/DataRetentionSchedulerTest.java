package com.attendance.scheduler;

import com.attendance.attendance.repository.AttendanceEventRepository;
import com.attendance.audit.repository.AuditLogRepository;
import com.attendance.config.PrivacyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DataRetentionSchedulerTest {

    @Mock AttendanceEventRepository attendanceEventRepository;
    @Mock AuditLogRepository auditLogRepository;

    private DataRetentionScheduler scheduler;
    private Instant fixedNow;

    @BeforeEach
    void setUp() {
        fixedNow = Instant.parse("2026-07-27T00:00:00Z");
        Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

        PrivacyProperties properties = new PrivacyProperties();
        properties.setLocationRetentionDays(90);
        properties.setAuditLogRetentionDays(365);

        scheduler = new DataRetentionScheduler(
                attendanceEventRepository, auditLogRepository, properties, clock);
    }

    @Test
    @DisplayName("위치정보는 locationRetentionDays 이전 데이터를, 감사로그는 auditLogRetentionDays 이전 데이터를 각각 삭제한다")
    void purgeExpiredData_usesCorrectCutoffsPerDataType() {
        given(attendanceEventRepository.deleteByEventAtBefore(any())).willReturn(3);
        given(auditLogRepository.deleteByCreatedAtBefore(any())).willReturn(7);

        scheduler.purgeExpiredData();

        ArgumentCaptor<Instant> eventCutoff = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> auditCutoff = ArgumentCaptor.forClass(Instant.class);
        verify(attendanceEventRepository).deleteByEventAtBefore(eventCutoff.capture());
        verify(auditLogRepository).deleteByCreatedAtBefore(auditCutoff.capture());

        assertThat(eventCutoff.getValue()).isEqualTo(fixedNow.minus(90, ChronoUnit.DAYS));
        assertThat(auditCutoff.getValue()).isEqualTo(fixedNow.minus(365, ChronoUnit.DAYS));
    }
}
