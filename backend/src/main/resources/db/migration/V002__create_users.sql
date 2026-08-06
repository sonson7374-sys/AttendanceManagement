-- V002: 사용자 및 단말기 테이블 생성

CREATE TABLE users (
    id                   BIGSERIAL PRIMARY KEY,
    email                VARCHAR(100)  NOT NULL UNIQUE,
    password             VARCHAR(200)  NOT NULL,
    name                 VARCHAR(50)   NOT NULL,
    employee_number      VARCHAR(30)   UNIQUE,
    phone                VARCHAR(20),
    company_id           BIGINT        REFERENCES companies(id),
    organization_id      BIGINT        REFERENCES organizations(id),
    job_title            VARCHAR(50),
    employment_type      VARCHAR(30),
    hire_date            DATE,
    resign_date          DATE,
    default_workplace_id BIGINT,
    work_schedule_id     BIGINT,
    role                 VARCHAR(20)   NOT NULL DEFAULT 'EMPLOYEE',
    status               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_users_role   CHECK (role IN ('EMPLOYEE','MANAGER','HR_ADMIN','SYSTEM_ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','INACTIVE','LOCKED'))
);

CREATE TABLE user_devices (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id),
    device_id       VARCHAR(100)  NOT NULL,
    device_platform VARCHAR(20)   NOT NULL,
    device_name     VARCHAR(100),
    fcm_token       VARCHAR(500),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    registered_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_seen_at    TIMESTAMPTZ,
    UNIQUE (user_id, device_id),
    CONSTRAINT chk_device_platform CHECK (device_platform IN ('ANDROID','IOS'))
);

CREATE INDEX idx_users_email          ON users(email);
CREATE INDEX idx_users_company_id     ON users(company_id, status);
CREATE INDEX idx_users_organization   ON users(organization_id);
CREATE INDEX idx_user_devices_user_id ON user_devices(user_id, active);
