CREATE TABLE establishments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    time_zone VARCHAR(50) NOT NULL DEFAULT 'America/Sao_Paulo',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_establishments_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT ck_establishments_time_zone_not_blank
        CHECK (BTRIM(time_zone) <> '')
);
