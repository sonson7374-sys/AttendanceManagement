package com.attendance.auth;

import com.attendance.auth.dto.LoginRequest;
import com.attendance.auth.jwt.JwtProperties;
import com.attendance.auth.jwt.JwtTokenProvider;
import com.attendance.auth.repository.BlacklistedTokenRepository;
import com.attendance.auth.repository.RefreshTokenRepository;
import com.attendance.auth.service.AuthService;
import com.attendance.audit.service.AuditLogService;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.config.SecurityPolicyProperties;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserRole;
import com.attendance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock BlacklistedTokenRepository blacklistedTokenRepository;
    @Mock AuditLogService auditLogService;

    private AuthService authService;
    private User testUser;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-for-testing-only-must-be-256-bits-long-abcdef");
        props.setAccessTokenExpireSeconds(1800);
        props.setRefreshTokenExpireDays(14);
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(props, Clock.systemUTC());

        SecurityPolicyProperties securityPolicyProperties = new SecurityPolicyProperties();
        securityPolicyProperties.setMaxLoginFailures(5);

        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, refreshTokenRepository,
                blacklistedTokenRepository, securityPolicyProperties, auditLogService, Clock.systemUTC());

        testUser = User.builder()
                .email("test@example.com")
                .password("$2a$10$hashedpw")
                .name("홍길동")
                .role(UserRole.EMPLOYEE)
                .build();
        ReflectionTestUtils.setField(testUser, "id", 1L);
    }

    private LoginRequest buildRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "password", password);
        return req;
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인 시 INVALID_CREDENTIALS")
    void login_emailNotFound() {
        given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(buildRequest("unknown@example.com", "pw")))
                .isInstanceOf(AttendanceException.class)
                .extracting(e -> ((AttendanceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인 시 INVALID_CREDENTIALS")
    void login_wrongPassword() {
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches(any(), any())).willReturn(false);

        assertThatThrownBy(() -> authService.login(buildRequest("test@example.com", "wrong")))
                .isInstanceOf(AttendanceException.class)
                .extracting(e -> ((AttendanceException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("정상 로그인 시 accessToken과 refreshToken 반환")
    void login_success() {
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches(any(), any())).willReturn(true);

        var response = authService.login(buildRequest("test@example.com", "pw"));

        org.assertj.core.api.Assertions.assertThat(response.getAccessToken()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(response.getRefreshToken()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(response.getRole()).isEqualTo("EMPLOYEE");
    }

    @Test
    @DisplayName("로그인 실패가 최대 허용 횟수에 도달하면 계정이 자동 잠기고 감사 로그가 남는다")
    void login_locksAccountAfterMaxFailures() {
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches(any(), any())).willReturn(false);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(buildRequest("test@example.com", "wrong")))
                    .isInstanceOf(AttendanceException.class);
        }

        org.assertj.core.api.Assertions.assertThat(testUser.isLocked()).isTrue();
        verify(auditLogService).record(eq(1L), any(), eq("ACCOUNT_AUTO_LOCKED"), any(), any(), any());
    }
}
