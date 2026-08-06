-- V006: 초기 시스템 관리자 계정 및 기본 데이터 삽입
-- 비밀번호: Admin1234! (BCrypt - 운영환경에서 즉시 변경 필수)

INSERT INTO companies (name, active)
VALUES ('기본 회사', TRUE);

-- BCrypt hash of 'Admin1234!' (cost=10) — 운영 환경 배포 후 즉시 변경 필수
INSERT INTO users (email, password, name, employee_number, company_id, role, status)
VALUES (
    'admin@attendance.local',
    '$2a$10$P9442Of67q4AbpY4dmClBuAKhNaXze0MnhU/PKP8t8cRqSDeDUeYG',
    '시스템 관리자',
    'SYS-0001',
    1,
    'SYSTEM_ADMIN',
    'ACTIVE'
);
