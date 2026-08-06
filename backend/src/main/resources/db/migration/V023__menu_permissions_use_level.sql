-- 메뉴관리(menu_permissions) 조회 기준을 로그인 권한(role)에서 권한레벨(level, 그룹코드 LEVEL_ROLL)로 전환한다.
-- level 컬럼은 신규 도입 당시 기존 행이 전부 'EMPLOYEE'로 백필되었기 때문에, 그대로 두면 실제
-- 시스템관리자/인사담당자 계정도 EMPLOYEE 레벨의(제약이 많은) 메뉴 설정을 적용받아 화면 접근이 막힌다.
-- 아직 개인화(level 수동 지정)가 되지 않은 계정만 role에 대응하는 LEVEL_ROLL 코드로 보정한다.
UPDATE users SET level = 'SYSADMIN' WHERE role = 'SYSTEM_ADMIN' AND level = 'EMPLOYEE';
UPDATE users SET level = 'HRADMIN' WHERE role = 'HR_ADMIN' AND level = 'EMPLOYEE';

-- 기존에 USER_ROLE 코드 'MANAGER' 기준으로 설정해둔 메뉴 권한을, 동등한 LEVEL_ROLL 코드 'PARTLEAD'로 이관한다.
UPDATE menu_permissions SET role = 'PARTLEAD' WHERE role = 'MANAGER';
