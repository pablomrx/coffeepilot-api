CREATE TABLE stock_movements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    coffee_shop_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,

    type VARCHAR(20) NOT NULL,

    quantity_delta NUMERIC(14, 3) NOT NULL,
    balance_after NUMERIC(14, 3) NOT NULL,

    reason VARCHAR(30),
    description VARCHAR(500),

    created_by BIGINT NOT NULL,

    reverses_movement_id BIGINT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_stock_movements_coffee_shop
        FOREIGN KEY (coffee_shop_id)
        REFERENCES coffee_shops (id),

    CONSTRAINT fk_stock_movements_ingredient
        FOREIGN KEY (ingredient_id, coffee_shop_id)
        REFERENCES ingredients (id, coffee_shop_id),

    CONSTRAINT fk_stock_movements_created_by
        FOREIGN KEY (created_by)
        REFERENCES users (id),

    CONSTRAINT fk_stock_movements_reverses_movement
        FOREIGN KEY (reverses_movement_id, coffee_shop_id)
        REFERENCES stock_movements (id, coffee_shop_id),

    CONSTRAINT ck_stock_movements_type
        CHECK (
            type IN (
                'INITIAL',
                'ENTRY',
                'ADJUSTMENT',
                'CONSUMPTION',
                'REVERSAL'
            )
        ),

    CONSTRAINT ck_stock_movements_reason
        CHECK (
            reason IS NULL
            OR reason IN (
                'LOSS',
                'EXPIRED',
                'DAMAGED',
                'COUNT_DIFFERENCE',
                'CORRECTION',
                'OTHER'
            )
        ),

    CONSTRAINT ck_stock_movements_quantity_non_zero
        CHECK (quantity_delta <> 0),

    CONSTRAINT ck_stock_movements_balance_non_negative
        CHECK (balance_after >= 0),

    CONSTRAINT ck_stock_movements_positive_types
        CHECK (
            type NOT IN ('INITIAL', 'ENTRY')
            OR quantity_delta > 0
        ),

    CONSTRAINT ck_stock_movements_consumption_negative
        CHECK (
            type <> 'CONSUMPTION'
            OR quantity_delta < 0
        ),

    CONSTRAINT ck_stock_movements_description_not_blank
        CHECK (
            description IS NULL
            OR BTRIM(description) <> ''
        ),

    CONSTRAINT ck_stock_movements_adjustment_requires_reason
        CHECK (
            type <> 'ADJUSTMENT'
            OR (
                reason IS NOT NULL
                AND description IS NOT NULL
                AND BTRIM(description) <> ''
            )
        ),

    CONSTRAINT ck_stock_movements_reason_only_adjustment
        CHECK (
            type = 'ADJUSTMENT'
            OR reason IS NULL
        ),

    CONSTRAINT ck_stock_movements_reason_negative_delta
        CHECK (
            reason IS NULL
            OR reason NOT IN ('LOSS', 'EXPIRED', 'DAMAGED')
            OR quantity_delta < 0
        ),

    CONSTRAINT ck_stock_movements_reversal_reference
        CHECK (
            (
                type = 'REVERSAL'
                AND reverses_movement_id IS NOT NULL
            )
            OR (
                type <> 'REVERSAL'
                AND reverses_movement_id IS NULL
            )
        ),

    CONSTRAINT ck_stock_movements_not_reverse_itself
        CHECK (
            reverses_movement_id IS NULL
            OR reverses_movement_id <> id
        ),

    CONSTRAINT uq_stock_movements_id_coffee_shop
        UNIQUE (id, coffee_shop_id)
);

CREATE UNIQUE INDEX uq_stock_movements_reverses_movement
    ON stock_movements (reverses_movement_id)
    WHERE reverses_movement_id IS NOT NULL;

CREATE UNIQUE INDEX uq_stock_movements_initial_per_ingredient
    ON stock_movements (ingredient_id)
    WHERE type = 'INITIAL';

CREATE INDEX idx_stock_movements_ingredient_history
    ON stock_movements (
        coffee_shop_id,
        ingredient_id,
        created_at DESC,
        id DESC
    );

CREATE INDEX idx_stock_movements_coffee_shop_date
    ON stock_movements (
        coffee_shop_id,
        created_at DESC,
        id DESC
    );
