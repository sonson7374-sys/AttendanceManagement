-- V008: 알림 테이블 생성

CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id),
    type         VARCHAR(30)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    message      VARCHAR(500) NOT NULL,
    related_type VARCHAR(50),
    related_id   BIGINT,
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_notification_type CHECK (type IN (
        'CHANGE_REQUEST_SUBMITTED','CHANGE_REQUEST_APPROVED','CHANGE_REQUEST_REJECTED',
        'ATTENDANCE_CORRECTED','ATTENDANCE_CLOSED','ATTENDANCE_REOPENED','GENERAL'
    ))
);

CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read, created_at);
