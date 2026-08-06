-- V016: 사번(employee_number) 길이를 VARCHAR(6)으로 축소
-- 기존 값이 전부 6자 이내임을 확인 후 적용.

ALTER TABLE users ALTER COLUMN employee_number TYPE VARCHAR(6);
