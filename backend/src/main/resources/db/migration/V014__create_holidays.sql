-- V014: 공휴일 테이블 — 결근 자동 처리·지각 판정에서 공휴일을 제외하기 위함

CREATE TABLE holidays (
    id         BIGSERIAL PRIMARY KEY,
    holiday_date DATE NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_holidays_date UNIQUE (holiday_date)
);

-- 2026년 대한민국 법정공휴일 (초기 시드 데이터)
INSERT INTO holidays (holiday_date, name) VALUES
    ('2026-01-01', '신정'),
    ('2026-02-16', '설날 연휴'),
    ('2026-02-17', '설날'),
    ('2026-02-18', '설날 연휴'),
    ('2026-03-01', '삼일절'),
    ('2026-05-05', '어린이날'),
    ('2026-05-24', '부처님오신날'),
    ('2026-06-06', '현충일'),
    ('2026-08-15', '광복절'),
    ('2026-09-24', '추석 연휴'),
    ('2026-09-25', '추석'),
    ('2026-09-26', '추석 연휴'),
    ('2026-10-03', '개천절'),
    ('2026-10-09', '한글날'),
    ('2026-12-25', '크리스마스');
