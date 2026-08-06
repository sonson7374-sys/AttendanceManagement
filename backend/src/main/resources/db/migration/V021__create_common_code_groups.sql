-- V021: 공통코드 그룹코드 테이블 생성 (권한관리 > 공통코드관리 화면에서 그룹코드 자체를 관리)

CREATE TABLE common_code_groups (
    id           BIGSERIAL PRIMARY KEY,
    group_code   VARCHAR(50)  NOT NULL UNIQUE,
    group_name   VARCHAR(100) NOT NULL,
    description  VARCHAR(200),
    protected    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO common_code_groups (group_code, group_name, description, protected) VALUES
    ('USER_ROLE', '사용자 역할', '로그인 계정의 역할(권한 등급) 코드', TRUE);

-- 기존 common_codes.group_code가 그룹코드 테이블을 참조하도록 FK 추가.
ALTER TABLE common_codes
    ADD CONSTRAINT common_codes_group_code_fkey
    FOREIGN KEY (group_code) REFERENCES common_code_groups(group_code);
