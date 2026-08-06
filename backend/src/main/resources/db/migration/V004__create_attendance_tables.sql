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
