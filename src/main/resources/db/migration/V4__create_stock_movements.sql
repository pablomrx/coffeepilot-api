CREATE TABLE stock_movements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    establishment_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,

    type VARCHAR(20) NOT NULL,

    quantity_delta NUMERIC(14, 3) NOT NULL,
    balance_after NUMERIC(14, 3) NOT NULL,

    reason VARCHAR(30),
    description VARCHAR(500),

    created_by BIGINT NOT NULL,

    reverses_movement_id BIGINT,

    order_id BIGINT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_stock_movements_establishment
        FOREIGN KEY (establishment_id)
        REFERENCES establishments (id),

    CONSTRAINT fk_stock_movements_ingredient
        FOREIGN KEY (ingredient_id, establishment_id)
        REFERENCES ingredients (id, establishment_id),

    CONSTRAINT fk_stock_movements_created_by
        FOREIGN KEY (created_by, establishment_id)
        REFERENCES users (id, establishment_id),

    CONSTRAINT fk_stock_movements_reverses_movement
        FOREIGN KEY (reverses_movement_id, establishment_id)
        REFERENCES stock_movements (id, establishment_id),

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

    CONSTRAINT ck_stock_movements_reversal_requires_description
        CHECK (
            type <> 'REVERSAL'
            OR (
                description IS NOT NULL
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

    CONSTRAINT ck_stock_movements_order_reference
        CHECK (
            (
                type = 'CONSUMPTION'
                AND order_id IS NOT NULL
            )
            OR (
                type IN ('INITIAL', 'ENTRY', 'ADJUSTMENT')
                AND order_id IS NULL
            )
            OR type = 'REVERSAL'
        ),

    CONSTRAINT uq_stock_movements_id_establishment
        UNIQUE (id, establishment_id)
);

CREATE UNIQUE INDEX uq_stock_movements_reverses_movement
    ON stock_movements (reverses_movement_id)
    WHERE reverses_movement_id IS NOT NULL;

CREATE UNIQUE INDEX uq_stock_movements_initial_per_ingredient
    ON stock_movements (ingredient_id)
    WHERE type = 'INITIAL';

CREATE UNIQUE INDEX uq_stock_movements_consumption_per_order_ingredient
    ON stock_movements (order_id, ingredient_id)
    WHERE type = 'CONSUMPTION';

CREATE INDEX idx_stock_movements_ingredient_history
    ON stock_movements (
        establishment_id,
        ingredient_id,
        created_at DESC,
        id DESC
    );

CREATE INDEX idx_stock_movements_establishment_date
    ON stock_movements (
        establishment_id,
        created_at DESC,
        id DESC
    );

CREATE INDEX idx_stock_movements_order
    ON stock_movements (establishment_id, order_id)
    WHERE order_id IS NOT NULL;
