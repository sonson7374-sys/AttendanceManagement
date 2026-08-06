-- V011: 근무제 유형 및 지각/조퇴/야간/휴일 기준값 컬럼 추가

ALTER TABLE work_schedules ADD COLUMN schedule_type VARCHAR(30) NOT NULL DEFAULT 'FIXED';
ALTER TABLE work_schedules ADD COLUMN late_threshold_minutes INT NOT NULL DEFAULT 0;
ALTER TABLE work_schedules ADD COLUMN early_leave_threshold_minutes INT NOT NULL DEFAULT 0;
ALTER TABLE work_schedules ADD COLUMN break_minutes INT NOT NULL DEFAULT 60;
ALTER TABLE work_schedules ADD COLUMN night_shift_start TIME;
ALTER TABLE work_schedules ADD COLUMN night_shift_end TIME;
ALTER TABLE work_schedules ADD COLUMN holiday_work_threshold_minutes INT NOT NULL DEFAULT 0;
