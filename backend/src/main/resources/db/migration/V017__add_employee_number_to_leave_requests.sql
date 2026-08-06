-- V017: leave_requests에 employee_number 컬럼 추가 (신청자 사번 스냅샷)

ALTER TABLE leave_requests ADD COLUMN employee_number VARCHAR(6);

UPDATE leave_requests lr
SET employee_number = u.employee_number
FROM users u
WHERE u.id = lr.requester_id;

ALTER TABLE leave_requests ALTER COLUMN employee_number SET NOT NULL;
