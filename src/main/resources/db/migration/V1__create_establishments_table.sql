CREATE TABLE establishments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    time_zone VARCHAR(50) NOT NULL DEFAULT 'America/Sao_Paulo',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    is_demo BOOLEAN NOT NULL DEFAULT FALSE,
    demo_expires_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_establishments_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT ck_establishments_time_zone_not_blank
        CHECK (BTRIM(time_zone) <> ''),

    CONSTRAINT ck_establishments_demo_expiry
        CHECK (is_demo = (demo_expires_at IS NOT NULL)),

    CONSTRAINT ck_establishments_demo_expiry_after_creation
        CHECK (demo_expires_at IS NULL OR demo_expires_at > created_at)
);

CREATE INDEX idx_establishments_demo_expires_at
    ON establishments (demo_expires_at)
    WHERE is_demo;
