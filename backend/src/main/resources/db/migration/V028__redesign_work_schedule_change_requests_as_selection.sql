-- 근무제 변경요청을 "새 근무제 정의 제출" 방식에서 "기존 근무제 중 선택" 방식으로 재설계.
-- 이 테이블은 신규 기능(V027)이며 운영 데이터가 없으므로 드롭 후 재생성한다.
DROP TABLE work_schedule_change_requests;

CREATE TABLE work_schedule_change_requests (
    id BIGSERIAL PRIMARY KEY,
    requester_id BIGINT NOT NULL REFERENCES users(id),
    current_work_schedule_id BIGINT REFERENCES work_schedules(id),
    target_work_schedule_id BIGINT NOT NULL REFERENCES work_schedules(id),
    effective_month DATE NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    current_approver_id BIGINT REFERENCES users(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_schedule_change_requests_requester ON work_schedule_change_requests (requester_id);
CREATE INDEX idx_work_schedule_change_requests_status ON work_schedule_change_requests (status);
