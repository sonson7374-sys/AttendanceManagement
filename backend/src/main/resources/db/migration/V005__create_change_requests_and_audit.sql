-- V005: 근태 수정 요청, 승인 이력, 감사 로그 테이블 생성

CREATE TABLE attendance_change_requests (
    id                   BIGSERIAL PRIMARY KEY,
    requester_id         BIGINT       NOT NULL REFERENCES users(id),
    record_id            BIGINT       REFERENCES attendance_records(id),
    target_date          DATE         NOT NULL,
    change_type          VARCHAR(30)  NOT NULL,
    requested_check_in   TIMESTAMPTZ,
    requested_check_out  TIMESTAMPTZ,
    requested_workplace_id BIGINT     REFERENCES workplaces(id),
    reason               TEXT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    current_approver_id  BIGINT       REFERENCES users(id),
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_change_request_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELED'))
);

CREATE TABLE approval_histories (
    id          BIGSERIAL PRIMARY KEY,
    request_id  BIGINT      NOT NULL REFERENCES attendance_change_requests(id),
    approver_id BIGINT      NOT NULL REFERENCES users(id),
    action      VARCHAR(20) NOT NULL,
    comment     TEXT,
    acted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_approval_action CHECK (action IN ('APPROVED','REJECTED','CANCELED'))
);

CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    actor_id     BIGINT      REFERENCES users(id),
    actor_email  VARCHAR(100),
    action       VARCHAR(100) NOT NULL,
    target_type  VARCHAR(50),
    target_id    BIGINT,
    detail       JSONB,
    ip_address   VARCHAR(45),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_change_requests_status   ON attendance_change_requests(status, current_approver_id);
CREATE INDEX idx_change_requests_requester ON attendance_change_requests(requester_id, status);
CREATE INDEX idx_audit_logs_target        ON audit_logs(target_type, target_id, created_at);
CREATE INDEX idx_audit_logs_actor         ON audit_logs(actor_id, created_at);
