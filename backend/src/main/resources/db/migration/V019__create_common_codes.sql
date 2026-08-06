-- V019: 공통코드 관리 테이블 생성 (권한관리 > 공통코드관리 화면에서 사용)
-- users.role(EMPLOYEE/MANAGER/HR_ADMIN/SYSTEM_ADMIN)은 여전히 Java enum + DB CHECK
-- 제약 + @PreAuthorize 어노테이션으로 실제 인가가 이뤄진다. 이 테이블은 화면 표시용
-- 코드 목록(라벨/설명/순서/활성화)을 관리자가 직접 편집할 수 있게 해줄 뿐이며,
-- 새로 추가한 코드가 자동으로 API 권한을 얻지는 않는다.

CREATE TABLE common_codes (
    id             BIGSERIAL PRIMARY KEY,
    group_code     VARCHAR(50)  NOT NULL,
    code           VARCHAR(50)  NOT NULL,
    code_name      VARCHAR(100) NOT NULL,
    description    VARCHAR(200),
    display_order  INT          NOT NULL DEFAULT 0,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    protected      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (group_code, code)
);

INSERT INTO common_codes (group_code, code, code_name, display_order, active, protected) VALUES
    ('USER_ROLE', 'EMPLOYEE',     '직원',       1, TRUE, TRUE),
    ('USER_ROLE', 'MANAGER',      '팀장',       2, TRUE, TRUE),
    ('USER_ROLE', 'HR_ADMIN',     '인사담당자', 3, TRUE, TRUE),
    ('USER_ROLE', 'SYSTEM_ADMIN', '시스템관리자', 4, TRUE, TRUE);

CREATE INDEX idx_common_codes_group ON common_codes(group_code, display_order);
