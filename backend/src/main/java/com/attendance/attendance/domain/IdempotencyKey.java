package com.attendance.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 출근·퇴근 요청의 Idempotency-Key 중복 제출을 막기 위한 기록. PK(unique) 제약을 이용해
 * 동시 요청에서도 원자적으로 "이미 처리 중/처리됨"을 판정한다(Redis SETNX와 동일한 역할).
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    @Id
    @Column(name = "idempotency_key", length = 200)
    private String key;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IdempotencyKey(String key, Instant expiresAt) {
        this.key = key;
        this.expiresAt = expiresAt;
    }
}
