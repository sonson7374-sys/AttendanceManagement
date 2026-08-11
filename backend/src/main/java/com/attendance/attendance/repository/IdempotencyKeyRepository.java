package com.attendance.attendance.repository;

import com.attendance.attendance.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    // JPA save()는 수동 할당된 @Id에 대해 merge(있으면 갱신, 없으면 삽입)로 동작해서 중복 키를
    // 조용히 덮어써버린다(Redis SETNX와 다른 동작). 그래서 DB의 ON CONFLICT DO NOTHING으로
    // "이미 존재하면 아무 일도 하지 않는다"를 원자적으로 보장하고, 삽입된 행 수(0 또는 1)로
    // 중복 여부를 판정한다.
    @Modifying
    @Query(value = "INSERT INTO idempotency_keys (idempotency_key, expires_at) VALUES (:key, :expiresAt) "
            + "ON CONFLICT (idempotency_key) DO NOTHING", nativeQuery = true)
    int tryInsert(@Param("key") String key, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
