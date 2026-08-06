-- V015: 휴일 유형(공휴일/대체공휴일/회사휴일) 추가, 휴가 기간 조회용 인덱스 추가
-- 목적: 관리자웹 "휴일/휴가 관리" 화면에서 유형별로 구분 표시하고,
--       월별 캘린더 조회 시 휴가 기간(start_at~end_at) 범위 검색이 인덱스를 타도록 함.

ALTER TABLE holidays
    ADD COLUMN holiday_type VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';

CREATE INDEX idx_leave_requests_period ON leave_requests(status, start_at, end_at);
