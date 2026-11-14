CREATE TABLE products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    establishment_id BIGINT NOT NULL,

    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),

    price NUMERIC(10, 2) NOT NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_products_establishment
        FOREIGN KEY (establishment_id)
        REFERENCES establishments (id),

    CONSTRAINT ck_products_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT ck_products_description_not_blank
        CHECK (
            description IS NULL
            OR BTRIM(description) <> ''
        ),

    CONSTRAINT ck_products_price_positive
        CHECK (price > 0),

    CONSTRAINT uq_products_id_establishment
        UNIQUE (id, establishment_id)
);

CREATE UNIQUE INDEX uq_products_establishment_name
    ON products (establishment_id, LOWER(BTRIM(name)));

CREATE INDEX idx_products_establishment_active
    ON products (establishment_id, is_active);
