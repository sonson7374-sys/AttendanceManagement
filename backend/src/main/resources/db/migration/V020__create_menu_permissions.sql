-- V020: 역할별 메뉴 표시·버튼 활성화 설정 테이블 생성 (권한관리 > 메뉴관리 화면에서 사용)
-- 행이 없는 (role, menu_key, action_key) 조합은 기본값 "표시/활성화"로 간주한다.
-- 관리자가 실제로 끈 조합만 이 테이블에 저장되므로, 메뉴·버튼이 새로 추가돼도
-- 기존 화면 동작(전부 노출)이 깨지지 않는다. 백엔드 @PreAuthorize 인가는 이 테이블과
-- 무관하게 그대로 유지되며, 이 설정은 프론트엔드 표시 여부만 제어한다.

CREATE TABLE menu_permissions (
    id            BIGSERIAL PRIMARY KEY,
    role          VARCHAR(50) NOT NULL,
    menu_key      VARCHAR(50) NOT NULL,
    action_key    VARCHAR(50) NOT NULL,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (role, menu_key, action_key)
);

CREATE INDEX idx_menu_permissions_role ON menu_permissions(role);
