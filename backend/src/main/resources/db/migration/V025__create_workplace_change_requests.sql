-- 근무지 변경 요청. 직원이 새로운 근무지를 제안(주소 검색 등으로 좌표까지 지정)하고 적용 예정일을 지정해
-- 신청하면, 승인함에서 관리자(SYSADMIN/HRADMIN/PRESIDENT)가 승인/반려한다. 승인 시 새 근무지가 실제로
-- 생성되고 신청자의 기존 배정(current_workplace_id, 있는 경우)을 대체해 새 근무지로 재배정된다.
CREATE TABLE workplace_change_requests (
    id BIGSERIAL PRIMARY KEY,
    requester_id BIGINT NOT NULL REFERENCES users(id),
    current_workplace_id BIGINT REFERENCES workplaces(id),
    name VARCHAR(100) NOT NULL,
    address VARCHAR(200),
    detail_address VARCHAR(200),
    type VARCHAR(30) NOT NULL DEFAULT 'OFFICE',
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    radius_meters INT NOT NULL DEFAULT 100,
    max_accuracy_meters INT,
    check_in_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    check_out_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    effective_date DATE NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resulting_workplace_id BIGINT REFERENCES workplaces(id),
    current_approver_id BIGINT REFERENCES users(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_workplace_change_requests_requester ON workplace_change_requests (requester_id);
CREATE INDEX idx_workplace_change_requests_status ON workplace_change_requests (status);
