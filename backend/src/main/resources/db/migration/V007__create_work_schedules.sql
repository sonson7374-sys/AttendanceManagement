-- V007: 근무제(WorkSchedule) 테이블 생성

CREATE TABLE work_schedules (
    id                      BIGSERIAL    PRIMARY KEY,
    company_id              BIGINT       NOT NULL REFERENCES companies(id),
    name                    VARCHAR(100) NOT NULL,                  -- 예: 기본 근무제, 교대 근무제
    work_start_time         TIME         NOT NULL DEFAULT '09:00',  -- 근무 시작 시각
    work_end_time           TIME         NOT NULL DEFAULT '18:00',  -- 기준 퇴근 시각
    required_work_minutes   INT          NOT NULL DEFAULT 480,      -- 소정 근무시간(분)
    overtime_threshold_min  INT          NOT NULL DEFAULT 480,      -- 연장근무 기준(분)
    is_default              BOOLEAN      NOT NULL DEFAULT FALSE,     -- 회사 기본 스케줄 여부
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_work_times CHECK (work_start_time < work_end_time),
    CONSTRAINT chk_required_minutes CHECK (required_work_minutes > 0 AND required_work_minutes <= 720)
);

-- 사용자별 근무제 배정 (미배정 시 회사 기본 스케줄 사용)
CREATE TABLE user_work_schedules (
    id                  BIGSERIAL    PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users(id),
    work_schedule_id    BIGINT       NOT NULL REFERENCES work_schedules(id),
    effective_from      DATE         NOT NULL DEFAULT CURRENT_DATE,
    effective_until     DATE,        -- NULL = 만료 없음
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_effective_range CHECK (effective_until IS NULL OR effective_until > effective_from)
);

CREATE INDEX idx_work_schedules_company ON work_schedules(company_id, active);
CREATE INDEX idx_user_work_schedules_user ON user_work_schedules(user_id, effective_from DESC);

-- 회사 1번의 기본 근무제 삽입 (기존 하드코딩된 09:00 ~ 18:00 을 DB로 이전)
INSERT INTO work_schedules (company_id, name, work_start_time, work_end_time, required_work_minutes, is_default)
VALUES (1, '기본 근무제', '09:00', '18:00', 480, TRUE);
