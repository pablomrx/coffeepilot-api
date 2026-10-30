CREATE TABLE ingredients (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    coffee_shop_id BIGINT NOT NULL,

    name VARCHAR(150) NOT NULL,
    unit VARCHAR(10) NOT NULL,

    current_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    minimum_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ingredients_coffee_shop
        FOREIGN KEY (coffee_shop_id)
        REFERENCES coffee_shops (id),

    CONSTRAINT ck_ingredients_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT ck_ingredients_unit
        CHECK (unit IN ('G', 'ML', 'UNIT')),

    CONSTRAINT ck_ingredients_current_quantity_non_negative
        CHECK (current_quantity >= 0),

    CONSTRAINT ck_ingredients_minimum_quantity_non_negative
        CHECK (minimum_quantity >= 0),

    CONSTRAINT ck_ingredients_unit_integer_quantities
        CHECK (
            unit <> 'UNIT'
            OR (
                current_quantity = TRUNC(current_quantity)
                AND minimum_quantity = TRUNC(minimum_quantity)
            )
        ),

    CONSTRAINT uq_ingredients_id_coffee_shop
        UNIQUE (id, coffee_shop_id)
);

CREATE UNIQUE INDEX uq_ingredients_coffee_shop_name
    ON ingredients (coffee_shop_id, LOWER(BTRIM(name)));

CREATE INDEX idx_ingredients_coffee_shop_active
    ON ingredients (coffee_shop_id, is_active);