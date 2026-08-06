package com.attendance.audit.service;

import com.attendance.audit.domain.AuditLog;
import com.attendance.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String actorEmail, String action,
                       String targetType, Long targetId, Map<String, Object> detail) {
        try {
            auditLogRepository.save(AuditLog.of(actorId, actorEmail, action, targetType, targetId, detail));
        } catch (Exception e) {
            log.error("감사 로그 저장 실패: action={} targetType={} targetId={}", action, targetType, targetId, e);
        }
    }
}
