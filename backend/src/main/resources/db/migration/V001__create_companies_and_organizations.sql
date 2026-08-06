-- V001: 회사 및 조직 테이블 생성

CREATE TABLE companies (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100)  NOT NULL UNIQUE,
    business_number  VARCHAR(20),
    address          VARCHAR(200),
    phone            VARCHAR(20),
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE organizations (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies(id),
    parent_id     BIGINT        REFERENCES organizations(id),
    name          VARCHAR(100)  NOT NULL,
    display_order INT,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_organizations_company_id ON organizations(company_id, active);
CREATE INDEX idx_organizations_parent_id ON organizations(parent_id);
