-- 직원 등록/수정 화면에 추가되는 "권한레벨" 선택 값을 저장하는 컬럼.
-- 그룹코드 LEVEL_ROLL(권한레벨: 사장/부문장/본부장/실장/팀장/파트장/직원 등)의 code 값이 그대로 저장된다.
-- role(UserRole)과 달리 실제 API 인가에는 쓰이지 않는 표시/분류 목적의 값이므로 CHECK 제약은 두지 않는다.
ALTER TABLE users ADD COLUMN level VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE';
ALTER TABLE users ALTER COLUMN level DROP DEFAULT;
