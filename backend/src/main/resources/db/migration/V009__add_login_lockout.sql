-- V009: 로그인 실패 횟수 기반 계정 자동 잠금, 관리자 비밀번호 초기화 시 강제 변경 플래그

ALTER TABLE users ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
