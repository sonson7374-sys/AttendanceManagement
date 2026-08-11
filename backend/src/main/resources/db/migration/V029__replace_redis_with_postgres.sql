-- Redis를 제거하고 리프레시 토큰 저장, 로그아웃 액세스 토큰 블랙리스트, 출퇴근 Idempotency-Key
-- 중복요청 방지를 전부 PostgreSQL로 옮긴다 (상용 환경에서 별도 Redis 구축이 필요 없도록).

-- 사용자당 하나의 활성 리프레시 토큰만 유지한다 (재로그인·재발급 시 덮어씀).
CREATE TABLE refresh_tokens (
    user_id    BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(1000) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 로그아웃된 액세스 토큰을 만료 전까지 무효화하기 위한 블랙리스트.
CREATE TABLE blacklisted_tokens (
    token      VARCHAR(1000) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_blacklisted_tokens_expires_at ON blacklisted_tokens(expires_at);

-- 출근·퇴근 요청의 Idempotency-Key 중복 제출 방지. PK(unique) 제약을 이용해 동시 요청에서도
-- 원자적으로 "이미 처리 중/처리됨"을 판정한다 (Redis SETNX와 동일한 역할).
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);
