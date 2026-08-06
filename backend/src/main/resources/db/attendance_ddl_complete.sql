-- ============================================
-- GPS 지오펜스 출퇴근 관리 시스템 DDL (완료)
-- 생성일시: 2026-08-03
-- ============================================

-- V001: 회사 및 조직 테이블 생성
CREATE TABLE companies (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100)  NOT NULL UNIQUE,
    business_number  VARCHAR(20),
    address          VARCHAR(200),
    phone            VARCHAR(20),
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE organizations (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies(id),
    parent_id     BIGINT        REFERENCES organizations(id),
    name          VARCHAR(100)  NOT NULL,
    display_order INT,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_organizations_company_id ON organizations(company_id, active);
CREATE INDEX idx_organizations_parent_id ON organizations(parent_id);

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

-- V003: 근무지 및 사용자-근무지 매핑 테이블 생성
CREATE TABLE workplaces (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT          NOT NULL REFERENCES companies(id),
    name          VARCHAR(100)    NOT NULL,
    address       VARCHAR(200),
    latitude      DECIMAL(10,7)   NOT NULL,
    longitude     DECIMAL(10,7)   NOT NULL,
    radius_meters INT             NOT NULL DEFAULT 100,
    valid_from    DATE,
    valid_to      DATE,
    active        BOOLEAN         NOT NULL DEFAULT TRUE,
    version       BIGINT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_workplace_latitude  CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT chk_workplace_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_workplace_radius    CHECK (radius_meters > 0)
);

CREATE TABLE user_workplaces (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT  NOT NULL REFERENCES users(id),
    workplace_id BIGINT  NOT NULL REFERENCES workplaces(id),
    valid_from   DATE,
    valid_to     DATE,
    assigned_by  BIGINT  REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, workplace_id)
);

ALTER TABLE users ADD CONSTRAINT fk_users_default_workplace
    FOREIGN KEY (default_workplace_id) REFERENCES workplaces(id);

CREATE INDEX idx_workplaces_company   ON workplaces(company_id, active);
CREATE INDEX idx_workplaces_active    ON workplaces(active, valid_from, valid_to);
CREATE INDEX idx_user_workplaces_user ON user_workplaces(user_id);

-- V004: 근태 기록 테이블 생성
CREATE TABLE attendance_records (
    id                        BIGSERIAL PRIMARY KEY,
    user_id                   BIGINT        NOT NULL REFERENCES users(id),
    work_date                 DATE          NOT NULL,
    status                    VARCHAR(20)   NOT NULL,
    check_in_at               TIMESTAMPTZ,
    check_out_at              TIMESTAMPTZ,
    workplace_id              BIGINT        REFERENCES workplaces(id),
    check_in_latitude         DECIMAL(10,7),
    check_in_longitude        DECIMAL(10,7),
    check_in_distance_meters  INT,
    check_in_accuracy_meters  DECIMAL(6,2),
    check_out_latitude        DECIMAL(10,7),
    check_out_longitude       DECIMAL(10,7),
    check_out_distance_meters INT,
    work_minutes              INT,
    break_minutes             INT,
    overtime_minutes          INT,
    is_late                   BOOLEAN       NOT NULL DEFAULT FALSE,
    is_early_leave            BOOLEAN       NOT NULL DEFAULT FALSE,
    is_closed                 BOOLEAN       NOT NULL DEFAULT FALSE,
    version                   BIGINT        NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, work_date),
    CONSTRAINT chk_attendance_status CHECK (status IN (
        'BEFORE_WORK','WORKING','BREAK','FINISHED','LATE',
        'EARLY_LEAVE','ABSENT','LEAVE','OUTSIDE_WORK','BUSINESS_TRIP','REMOTE_WORK'
    )),
    CONSTRAINT chk_check_in_lat  CHECK (check_in_latitude  IS NULL OR check_in_latitude  BETWEEN -90  AND 90),
    CONSTRAINT chk_check_in_lon  CHECK (check_in_longitude IS NULL OR check_in_longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_check_out_lat CHECK (check_out_latitude  IS NULL OR check_out_latitude  BETWEEN -90  AND 90),
    CONSTRAINT chk_check_out_lon CHECK (check_out_longitude IS NULL OR check_out_longitude BETWEEN -180 AND 180)
);

-- 원본 이벤트 불변 보존 테이블
CREATE TABLE attendance_events (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id),
    record_id        BIGINT       REFERENCES attendance_records(id),
    event_type       VARCHAR(20)  NOT NULL,
    event_at         TIMESTAMPTZ  NOT NULL,
    workplace_id     BIGINT       REFERENCES workplaces(id),
    latitude         DECIMAL(10,7),
    longitude        DECIMAL(10,7),
    accuracy_meters  DECIMAL(6,2),
    distance_meters  INT,
    device_id        VARCHAR(100),
    device_platform  VARCHAR(20),
    mock_detected    BOOLEAN      NOT NULL DEFAULT FALSE,
    raw_payload      JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_event_type CHECK (event_type IN ('CHECK_IN','CHECK_OUT','BREAK_START','BREAK_END'))
);

CREATE TABLE break_records (
    id          BIGSERIAL PRIMARY KEY,
    record_id   BIGINT      NOT NULL REFERENCES attendance_records(id),
    start_at    TIMESTAMPTZ NOT NULL,
    end_at      TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attendance_records_user_date ON attendance_records(user_id, work_date);
CREATE INDEX idx_attendance_records_date_status ON attendance_records(work_date, status);
CREATE INDEX idx_attendance_events_user_at ON attendance_events(user_id, event_at);
CREATE INDEX idx_break_records_record ON break_records(record_id);

-- V005: 근태 수정 요청 및 감사 로그 테이블 생성
CREATE TABLE change_requests (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES users(id),
    request_type  VARCHAR(30)   NOT NULL,
    target_date   DATE          NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    reason        TEXT,
    requested_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    processed_at  TIMESTAMPTZ,
    processed_by  BIGINT        REFERENCES users(id),
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_change_request_type  CHECK (request_type IN ('CHECK_IN','CHECK_OUT','WORKING_STATUS')),
    CONSTRAINT chk_change_request_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELED'))
);

CREATE TABLE approval_history (
    id              BIGSERIAL PRIMARY KEY,
    change_request_id BIGINT    NOT NULL REFERENCES change_requests(id),
    action          VARCHAR(20) NOT NULL,
    actor_id        BIGINT      NOT NULL REFERENCES users(id),
    comment         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_approval_action CHECK (action IN ('APPROVE','REJECT'))
);

-- 감사 로그 테이블
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT       NOT NULL REFERENCES users(id),
    action      VARCHAR(50)  NOT NULL,
    target_type VARCHAR(50)  NOT NULL,
    target_id   BIGINT,
    details     JSONB,
    ip_address  VARCHAR(45),
    user_agent  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_change_requests_user_date   ON change_requests(user_id, target_date);
CREATE INDEX idx_change_requests_status      ON change_requests(status, requested_at);
CREATE INDEX idx_approval_history_request    ON approval_history(change_request_id);
CREATE INDEX idx_audit_logs_actor             ON audit_logs(actor_id, created_at);
CREATE INDEX idx_audit_logs_target            ON audit_logs(target_type, target_id);

-- V007: 근무 일정 테이블 생성
CREATE TABLE work_schedules (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT        NOT NULL REFERENCES companies(id),
    name              VARCHAR(100)  NOT NULL,
    work_start_time   TIME          NOT NULL,
    work_end_time     TIME          NOT NULL,
    break_start_time  TIME,
    break_end_time    TIME,
    work_days         VARCHAR(20)  NOT NULL,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_work_days CHECK (work_days IN ('MON-FRI','MON-SAT','CUSTOM'))
);

ALTER TABLE users ADD CONSTRAINT fk_users_work_schedule
    FOREIGN KEY (work_schedule_id) REFERENCES work_schedules(id);

CREATE INDEX idx_work_schedules_company ON work_schedules(company_id, active);

-- V008: 알림 테이블 생성
CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    type        VARCHAR(30) NOT NULL,
    title       VARCHAR(100) NOT NULL,
    body        TEXT,
    data        JSONB,
    read_at     TIMESTAMPTZ,
    read        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_notification_type CHECK (type IN ('CHECK_IN_SUCCESS','CHECK_IN_FAILED','CHECK_OUT_SUCCESS','CHECK_OUT_FAILED','CHANGE_REQUEST_STATUS','LEAVE_REQUEST_STATUS','SYSTEM'))
);

CREATE INDEX idx_notifications_user_read ON notifications(user_id, read, created_at);

-- V009: 로그인 잠금 기능 추가
ALTER TABLE users ADD COLUMN if NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN if NOT EXISTS locked_until TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN if NOT EXISTS last_login_at TIMESTAMPTZ;

-- V010: 근무지 확장 (주소 상세 정보 etc)
ALTER TABLE workplaces ADD COLUMN if NOT EXISTS detail_address VARCHAR(200);
ALTER TABLE workplaces ADD COLUMN if NOT EXISTS description TEXT;

-- V011: 근무 일정 확장
ALTER TABLE work_schedules ADD COLUMN if NOT EXISTS late_allowed_minutes INT NOT NULL DEFAULT 0;
ALTER TABLE work_schedules ADD COLUMN if NOT EXISTS early_leave_allowed_minutes INT NOT NULL DEFAULT 0;
ALTER TABLE work_schedules ADD COLUMN if NOT EXISTS overtime_threshold_minutes INT NOT NULL DEFAULT 0;

-- V012: 휴가 및 외근 신청 테이블 생성
ALTER TABLE approval_histories DROP CONSTRAINT approval_histories_request_id_fkey;
ALTER TABLE approval_histories ADD COLUMN request_type VARCHAR(30) NOT NULL DEFAULT 'CHANGE_REQUEST';

CREATE TABLE leave_requests (
    id                   BIGSERIAL PRIMARY KEY,
    requester_id         BIGINT       NOT NULL REFERENCES users(id),
    request_type         VARCHAR(30)  NOT NULL,
    start_at             TIMESTAMPTZ  NOT NULL,
    end_at               TIMESTAMPTZ  NOT NULL,
    reason               TEXT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    current_approver_id  BIGINT       REFERENCES users(id),
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_leave_request_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELED')),
    CONSTRAINT chk_leave_request_dates CHECK (end_at >= start_at)
);

CREATE TABLE outside_work_requests (
    id                    BIGSERIAL PRIMARY KEY,
    requester_id          BIGINT       NOT NULL REFERENCES users(id),
    request_type          VARCHAR(30)  NOT NULL,
    start_at              TIMESTAMPTZ  NOT NULL,
    end_at                TIMESTAMPTZ  NOT NULL,
    reason                TEXT,
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    current_approver_id   BIGINT       REFERENCES users(id),
    destination_address   VARCHAR(500),
    destination_latitude  DECIMAL(10,7),
    destination_longitude DECIMAL(10,7),
    temp_radius_meters    INT,
    visit_purpose         VARCHAR(500),
    client_name           VARCHAR(200),
    expected_return_at    TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_outside_work_request_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELED')),
    CONSTRAINT chk_outside_work_request_dates CHECK (end_at >= start_at)
);

CREATE INDEX idx_leave_requests_requester ON leave_requests(requester_id, status);
CREATE INDEX idx_leave_requests_status ON leave_requests(status, current_approver_id);
CREATE INDEX idx_outside_work_requests_requester ON outside_work_requests(requester_id, status);
CREATE INDEX idx_outside_work_requests_status ON outside_work_requests(status, current_approver_id);

-- V013: 알림 유형 확장
ALTER TABLE notifications ADD COLUMN if NOT EXISTS is_pushed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notifications ADD COLUMN if NOT EXISTS push_sent_at TIMESTAMPTZ;

-- V014: 휴일 테이블 생성
CREATE TABLE holidays (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT        REFERENCES companies(id),
    date        DATE          NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    is_national BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (company_id, date)
);

CREATE INDEX idx_holidays_company_date ON holidays(company_id, date);

-- V015: 휴일 및 휴가 인덱스 확장
CREATE INDEX if NOT EXISTS idx_holidays_date ON holidays(date);
CREATE INDEX if NOT EXISTS idx_leave_requests_dates ON leave_requests(start_date, end_date);

-- V016: employee_number 길이 축소
ALTER TABLE users ALTER COLUMN employee_number TYPE VARCHAR(20);

-- V017: leave_requests에 employee_number 추가
ALTER TABLE leave_requests ADD COLUMN employee_number VARCHAR(20);

-- V018: leave_requests employee_number 외래키 추가
-- (조건부로, employees 테이블이 있는 경우)

-- V019: 공통 코드 테이블 생성
CREATE TABLE common_code_groups (
    id          BIGSERIAL PRIMARY KEY,
    group_code  VARCHAR(50)  NOT NULL UNIQUE,
    group_name  VARCHAR(100) NOT NULL,
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE common_codes (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT        NOT NULL REFERENCES common_code_groups(id),
    code            VARCHAR(50)   NOT NULL,
    code_name       VARCHAR(100)  NOT NULL,
    code_value      VARCHAR(200),
    display_order   INT,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, code)
);

CREATE INDEX idx_common_code_groups_code ON common_code_groups(group_code);
CREATE INDEX idx_common_codes_group ON common_codes(group_id, active);

-- V020: 메뉴 권한 테이블 생성
CREATE TABLE menu_permissions (
    id           BIGSERIAL PRIMARY KEY,
    menu_code    VARCHAR(50)  NOT NULL,
    menu_name    VARCHAR(100) NOT NULL,
    role         VARCHAR(20)  NOT NULL,
    permission   VARCHAR(20)  NOT NULL DEFAULT 'READ',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (menu_code, role, permission),
    CONSTRAINT chk_menu_permission CHECK (permission IN ('NONE','READ','WRITE','ADMIN'))
);

-- V021: common_code_groups 확장
ALTER TABLE common_code_groups ADD COLUMN if NOT EXISTS display_order INT;
ALTER TABLE common_code_groups ADD COLUMN if NOT EXISTS parent_group_code VARCHAR(50);

-- V022: 사용자에 레벨 추가
ALTER TABLE users ADD COLUMN if NOT EXISTS level INT;

-- V023: menu_permissions가 level을 사용하도록 수정
-- (role과 level을 함께 사용)

-- V024: 캘린더 이벤트 테이블 생성
CREATE TABLE calendar_events (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT        REFERENCES companies(id),
    user_id         BIGINT        REFERENCES users(id),
    event_type      VARCHAR(30)   NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    description     TEXT,
    start_date      DATE          NOT NULL,
    end_date        DATE,
    all_day         BOOLEAN       NOT NULL DEFAULT TRUE,
    color           VARCHAR(20),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_calendar_events_company ON calendar_events(company_id, start_date);
CREATE INDEX idx_calendar_events_user ON calendar_events(user_id, start_date);

-- V025: 근무지 변경 요청 테이블 생성
CREATE TABLE workplace_change_requests (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id),
    current_workplace_id BIGINT   REFERENCES workplaces(id),
    requested_workplace_id BIGINT NOT NULL REFERENCES workplaces(id),
    reason          TEXT,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    requested_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    processed_by    BIGINT        REFERENCES users(id),
    reject_reason   TEXT,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workplace_change_requests_user ON workplace_change_requests(user_id, requested_at);

-- V026: notification_type 너비 확장
ALTER TABLE notifications ALTER COLUMN type TYPE VARCHAR(50);

-- V027: 근무 일정 변경 요청 테이블 생성
CREATE TABLE work_schedule_change_requests (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id),
    current_schedule_id BIGINT    REFERENCES work_schedules(id),
    requested_schedule_id BIGINT   NOT NULL REFERENCES work_schedules(id),
    reason          TEXT,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    requested_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    processed_by    BIGINT        REFERENCES users(id),
    reject_reason   TEXT,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- V028: 근무 일정 변경 요청을 선택 기반으로 재설계
ALTER TABLE work_schedule_change_requests ADD COLUMN if NOT EXISTS date_pattern VARCHAR(100);