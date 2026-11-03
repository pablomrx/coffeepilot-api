CREATE TABLE products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    coffee_shop_id BIGINT NOT NULL,

    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),

    price NUMERIC(10, 2) NOT NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_products_coffee_shop
        FOREIGN KEY (coffee_shop_id)
        REFERENCES coffee_shops (id),

    CONSTRAINT ck_products_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT ck_products_description_not_blank
        CHECK (
            description IS NULL
            OR BTRIM(description) <> ''
    ),

    CONSTRAINT ck_products_price_positive
        CHECK (price > 0),

    CONSTRAINT uq_products_id_coffee_shop
        UNIQUE (id, coffee_shop_id)
);

CREATE UNIQUE INDEX uq_products_coffee_shop_name
    ON products (coffee_shop_id, LOWER(BTRIM(name)));

CREATE INDEX idx_products_coffee_shop_active
    ON products (coffee_shop_id, is_active);
