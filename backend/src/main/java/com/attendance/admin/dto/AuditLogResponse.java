package com.attendance.admin.dto;

import com.attendance.audit.domain.AuditLog;

import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorEmail,
        String action,
        String targetType,
        Long targetId,
        Map<String, Object> detail,
        String ipAddress,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getActorId(), log.getActorEmail(),
                log.getAction(), log.getTargetType(), log.getTargetId(),
                log.getDetail(), log.getIpAddress(), log.getCreatedAt()
        );
    }
}
