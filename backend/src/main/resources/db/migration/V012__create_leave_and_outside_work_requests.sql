-- V012: 휴가(연차/반차/시간차/병가/공가/연장근무/휴일근무) 및 외근·출장·재택근무 신청 테이블 생성
-- approval_histories는 attendance_change_requests 전용 FK를 갖고 있었으나,
-- 여러 신청 도메인(변경요청/휴가/외근)이 공유하는 polymorphic 이력 테이블로 확장한다.
-- (audit_logs의 target_type/target_id 패턴과 동일한 방식)

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
