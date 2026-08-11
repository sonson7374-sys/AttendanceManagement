package com.attendance.auth.service;

import com.attendance.auth.domain.BlacklistedToken;
import com.attendance.auth.domain.RefreshToken;
import com.attendance.auth.dto.LoginRequest;
import com.attendance.auth.dto.LoginResponse;
import com.attendance.auth.jwt.JwtTokenProvider;
import com.attendance.auth.repository.BlacklistedTokenRepository;
import com.attendance.auth.repository.RefreshTokenRepository;
import com.attendance.audit.service.AuditLogService;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.config.SecurityPolicyProperties;
import com.attendance.user.domain.User;
import com.attendance.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final SecurityPolicyProperties securityPolicyProperties;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AttendanceException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            boolean justLocked = user.recordLoginFailure(securityPolicyProperties.getMaxLoginFailures());
            if (justLocked) {
                auditLogService.record(user.getId(), user.getEmail(), "ACCOUNT_AUTO_LOCKED",
                        "USER", user.getId(), Map.of("reason", "max-login-failures exceeded"));
            }
            throw new AttendanceException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!user.isActive()) {
            throw new AttendanceException(user.isLocked() ? ErrorCode.ACCOUNT_LOCKED : ErrorCode.ACCOUNT_INACTIVE);
        }

        user.resetLoginFailures();

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        refreshTokenRepository.save(new RefreshToken(user.getId(), refreshToken,
                Instant.now(clock).plusSeconds(jwtTokenProvider.getRefreshTokenExpireSeconds())));

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .level(user.getLevel())
                .build();
    }

    @Transactional
    public LoginResponse refresh(String refreshToken) {
        Claims claims = jwtTokenProvider.parseToken(refreshToken);
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new AttendanceException(ErrorCode.INVALID_TOKEN);
        }

        Long userId = Long.parseLong(claims.getSubject());
        RefreshToken stored = refreshTokenRepository.findById(userId).orElse(null);
        if (stored == null || !refreshToken.equals(stored.getToken()) || stored.getExpiresAt().isBefore(Instant.now(clock))) {
            throw new AttendanceException(ErrorCode.INVALID_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AttendanceException(ErrorCode.USER_NOT_FOUND));

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        refreshTokenRepository.save(new RefreshToken(userId, newRefreshToken,
                Instant.now(clock).plusSeconds(jwtTokenProvider.getRefreshTokenExpireSeconds())));

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .level(user.getLevel())
                .build();
    }

    @Transactional
    public void logout(Long userId, String accessToken) {
        refreshTokenRepository.deleteById(userId);
        try {
            Claims claims = jwtTokenProvider.parseToken(accessToken);
            Instant expiresAt = claims.getExpiration().toInstant();
            if (expiresAt.isAfter(Instant.now(clock))) {
                blacklistedTokenRepository.save(new BlacklistedToken(accessToken, expiresAt));
            }
        } catch (AttendanceException e) {
            // 이미 만료된 토큰은 블랙리스트 불필요
        }
    }
}
