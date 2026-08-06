CREATE TABLE work_schedule_change_requests (
    id BIGSERIAL PRIMARY KEY,
    requester_id BIGINT NOT NULL REFERENCES users(id),
    current_work_schedule_id BIGINT REFERENCES work_schedules(id),
    name VARCHAR(100) NOT NULL,
    schedule_type VARCHAR(30) NOT NULL DEFAULT 'FIXED',
    work_start_time TIME NOT NULL,
    work_end_time TIME NOT NULL,
    required_work_minutes INT NOT NULL,
    overtime_threshold_min INT NOT NULL,
    late_threshold_minutes INT NOT NULL,
    early_leave_threshold_minutes INT NOT NULL,
    break_minutes INT NOT NULL,
    night_shift_start TIME,
    night_shift_end TIME,
    holiday_work_threshold_minutes INT NOT NULL,
    effective_date DATE NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resulting_work_schedule_id BIGINT REFERENCES work_schedules(id),
    current_approver_id BIGINT REFERENCES users(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_schedule_change_requests_requester ON work_schedule_change_requests (requester_id);
CREATE INDEX idx_work_schedule_change_requests_status ON work_schedule_change_requests (status);

ALTER TABLE notifications
    DROP CONSTRAINT chk_notification_type;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notification_type CHECK (type IN (
        'CHANGE_REQUEST_SUBMITTED', 'CHANGE_REQUEST_APPROVED', 'CHANGE_REQUEST_REJECTED',
        'LEAVE_REQUEST_SUBMITTED', 'LEAVE_REQUEST_APPROVED', 'LEAVE_REQUEST_REJECTED',
        'OUTSIDE_WORK_REQUEST_SUBMITTED', 'OUTSIDE_WORK_REQUEST_APPROVED', 'OUTSIDE_WORK_REQUEST_REJECTED',
        'WORKPLACE_CHANGE_REQUEST_SUBMITTED', 'WORKPLACE_CHANGE_REQUEST_APPROVED', 'WORKPLACE_CHANGE_REQUEST_REJECTED',
        'WORK_SCHEDULE_CHANGE_REQUEST_SUBMITTED', 'WORK_SCHEDULE_CHANGE_REQUEST_APPROVED', 'WORK_SCHEDULE_CHANGE_REQUEST_REJECTED',
        'ATTENDANCE_CORRECTED', 'ATTENDANCE_CLOSED', 'ATTENDANCE_REOPENED',
        'GENERAL'
    ));
