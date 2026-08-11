-- V030: 두 번째 회사(멀티테넌시) 지원
-- 1) 사번(employee_number) 유니크 제약을 전역 -> 회사 단위(company_id, employee_number)로 변경
-- 2) 회사당 기본 근무제(work_schedules.is_default)가 하나만 존재하도록 부분 유니크 인덱스 추가
-- 3) 회사2 + 회사2 소속 초기 SYSTEM_ADMIN 계정 부트스트랩

-- leave_requests.employee_number -> users.employee_number 단일 컬럼 FK는 사번이 회사별로만
-- 유일해지면 다른 회사의 동일 사번 사용자를 가리킬 수 있어 더 이상 안전하지 않다.
-- requester_id가 이미 사용자를 정확히(회사 구분 없이 고유 id로) 식별하므로 이 FK는 제거한다.
ALTER TABLE leave_requests DROP CONSTRAINT leave_requests_employee_number_fkey;

ALTER TABLE users DROP CONSTRAINT users_employee_number_key;
ALTER TABLE users ADD CONSTRAINT uq_users_company_employee_number UNIQUE (company_id, employee_number);

-- 기존 행 전부 company_id = 1 이므로 안전하게 NOT NULL 전환 가능
ALTER TABLE users ALTER COLUMN company_id SET NOT NULL;

CREATE UNIQUE INDEX uq_work_schedules_company_default ON work_schedules (company_id) WHERE is_default = true;

-- 회사2 부트스트랩 (플랫폼 운영자가 이후 실제 정보로 화면에서 수정)
-- 비밀번호: Admin1234! (BCrypt, V006과 동일 해시) — must_change_password로 최초 로그인 시 변경 강제
WITH new_company AS (
    INSERT INTO companies (name, active) VALUES ('새 회사', TRUE) RETURNING id
), new_org AS (
    INSERT INTO organizations (company_id, name, active)
    SELECT id, '본사', TRUE FROM new_company
    RETURNING id, company_id
), new_workplace AS (
    INSERT INTO workplaces (company_id, name, latitude, longitude, radius_meters, active)
    SELECT company_id, '본사(임시)', 37.5663, 126.9779, 500, TRUE FROM new_org
    RETURNING id
), new_schedule AS (
    INSERT INTO work_schedules (company_id, name, work_start_time, work_end_time, required_work_minutes, is_default)
    SELECT company_id, '기본 근무제', '09:00', '18:00', 480, TRUE FROM new_org
    RETURNING id
)
INSERT INTO users (email, password, name, employee_number, company_id, organization_id, role, level, status, must_change_password)
SELECT
    'admin2@attendance.local',
    '$2a$10$P9442Of67q4AbpY4dmClBuAKhNaXze0MnhU/PKP8t8cRqSDeDUeYG',
    '회사2 시스템 관리자',
    'SYS001',
    new_org.company_id,
    new_org.id,
    'SYSTEM_ADMIN',
    'SYSADMIN',
    'ACTIVE',
    TRUE
FROM new_org;
