package com.attendance.scheduler;

import com.attendance.attendance.repository.IdempotencyKeyRepository;
import com.attendance.auth.repository.BlacklistedTokenRepository;
import com.attendance.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 리프레시 토큰, 로그아웃 액세스 토큰 블랙리스트, Idempotency-Key는 원래 Redis의 TTL로 자동 만료되던
 * 값들이라 PostgreSQL로 옮긴 뒤에는 만료된 행을 직접 정리해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final Clock clock;

    // 매일 새벽 03:30 KST 실행 (DataRetentionScheduler의 03:00과 겹치지 않게 30분 뒤로 배치).
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now(clock);

        int deletedRefreshTokens = refreshTokenRepository.deleteByExpiresAtBefore(now);
        log.info("만료된 리프레시 토큰 삭제: count={}", deletedRefreshTokens);

        int deletedBlacklistedTokens = blacklistedTokenRepository.deleteByExpiresAtBefore(now);
        log.info("만료된 블랙리스트 토큰 삭제: count={}", deletedBlacklistedTokens);

        int deletedIdempotencyKeys = idempotencyKeyRepository.deleteByExpiresAtBefore(now);
        log.info("만료된 Idempotency-Key 삭제: count={}", deletedIdempotencyKeys);
    }
}
