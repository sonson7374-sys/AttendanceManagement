-- V003: 근무지 및 사용자-근무지 매핑 테이블 생성

CREATE TABLE workplaces (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT          NOT NULL REFERENCES companies(id),
    name          VARCHAR(100)    NOT NULL,
    address       VARCHAR(200),
    latitude      DECIMAL(10,7)   NOT NULL,
    longitude     DECIMAL(10,7)   NOT NULL,
    radius_meters INT             NOT NULL DEFAULT 100,
    valid_from    DATE,
    valid_to      DATE,
    active        BOOLEAN         NOT NULL DEFAULT TRUE,
    version       BIGINT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_workplace_latitude  CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT chk_workplace_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_workplace_radius    CHECK (radius_meters > 0)
);

CREATE TABLE user_workplaces (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT  NOT NULL REFERENCES users(id),
    workplace_id BIGINT  NOT NULL REFERENCES workplaces(id),
    valid_from   DATE,
    valid_to     DATE,
    assigned_by  BIGINT  REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, workplace_id)
);

ALTER TABLE users ADD CONSTRAINT fk_users_default_workplace
    FOREIGN KEY (default_workplace_id) REFERENCES workplaces(id);

CREATE INDEX idx_workplaces_company   ON workplaces(company_id, active);
CREATE INDEX idx_workplaces_active    ON workplaces(active, valid_from, valid_to);
CREATE INDEX idx_user_workplaces_user ON user_workplaces(user_id);
