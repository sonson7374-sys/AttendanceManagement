-- 일정관리(캘린더) 기능. 회사 공용 일정("전체")과 특정 직원의 개인 일정("개인")을 함께 관리한다.
-- 등록/수정/삭제는 권한레벨(SYSADMIN/HRADMIN/PRESIDENT)만 가능하고(애플리케이션 레벨에서 검증),
-- 조회는 인증된 모든 사용자가 가능하되 "개인" 일정은 대상 직원 본인에게만 노출된다.
CREATE TABLE calendar_events (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    all_day BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT,
    location VARCHAR(200),
    color VARCHAR(20),
    category VARCHAR(20) NOT NULL DEFAULT 'ETC',
    visibility VARCHAR(20) NOT NULL DEFAULT 'ALL',
    target_user_id BIGINT REFERENCES users(id),
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_calendar_events_visibility CHECK (visibility IN ('ALL', 'PERSONAL')),
    CONSTRAINT chk_calendar_events_category CHECK (category IN ('MEETING', 'EVENT', 'NOTICE', 'ETC'))
);

CREATE INDEX idx_calendar_events_range ON calendar_events (start_at, end_at);
CREATE INDEX idx_calendar_events_target_user ON calendar_events (target_user_id);
