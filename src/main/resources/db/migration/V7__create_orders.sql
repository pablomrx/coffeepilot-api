CREATE TABLE order_sequences (
    coffee_shop_id BIGINT PRIMARY KEY,

    last_number BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_order_sequences_coffee_shop
        FOREIGN KEY (coffee_shop_id)
        REFERENCES coffee_shops (id),

    CONSTRAINT ck_order_sequences_last_number_non_negative
        CHECK (last_number >= 0)
);

CREATE TABLE orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    coffee_shop_id BIGINT NOT NULL,
    order_number BIGINT NOT NULL,

    status VARCHAR(20) NOT NULL,

    table_reference VARCHAR(50),
    notes VARCHAR(500),

    total NUMERIC(15, 2) NOT NULL,

    created_by BIGINT NOT NULL,

    completed_at TIMESTAMPTZ,

    cancelled_at TIMESTAMPTZ,
    cancelled_by BIGINT,
    cancelled_from_status VARCHAR(20),
    cancellation_reason VARCHAR(500),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_orders_coffee_shop
        FOREIGN KEY (coffee_shop_id)
        REFERENCES coffee_shops (id),

    CONSTRAINT fk_orders_created_by
        FOREIGN KEY (created_by, coffee_shop_id)
        REFERENCES users (id, coffee_shop_id),

    CONSTRAINT fk_orders_cancelled_by
        FOREIGN KEY (cancelled_by, coffee_shop_id)
        REFERENCES users (id, coffee_shop_id),

    CONSTRAINT ck_orders_status
        CHECK (status IN ('PENDING', 'IN_PREPARATION', 'COMPLETED', 'CANCELLED')),

    CONSTRAINT ck_orders_number_positive
        CHECK (order_number > 0),

    CONSTRAINT ck_orders_total_positive
        CHECK (total > 0),

    CONSTRAINT ck_orders_table_reference_not_blank
        CHECK (table_reference IS NULL OR BTRIM(table_reference) <> ''),

    CONSTRAINT ck_orders_notes_not_blank
        CHECK (notes IS NULL OR BTRIM(notes) <> ''),

    CONSTRAINT ck_orders_completed_at
        CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),

    CONSTRAINT ck_orders_cancelled_from_status
        CHECK (
            cancelled_from_status IS NULL
            OR cancelled_from_status IN ('PENDING', 'IN_PREPARATION')
        ),

    CONSTRAINT ck_orders_cancellation_fields
        CHECK (
            (
                status = 'CANCELLED'
                AND cancelled_at IS NOT NULL
                AND cancelled_by IS NOT NULL
                AND cancelled_from_status IS NOT NULL
            )
            OR (
                status <> 'CANCELLED'
                AND cancelled_at IS NULL
                AND cancelled_by IS NULL
                AND cancelled_from_status IS NULL
                AND cancellation_reason IS NULL
            )
        ),

    CONSTRAINT ck_orders_cancellation_reason
        CHECK (
            (cancellation_reason IS NULL OR BTRIM(cancellation_reason) <> '')
             AND (
                cancelled_from_status IS DISTINCT FROM 'IN_PREPARATION'
                OR cancellation_reason IS NOT NULL
            )
        ),

    CONSTRAINT uq_orders_id_coffee_shop
        UNIQUE (id, coffee_shop_id),

    CONSTRAINT uq_orders_coffee_shop_number
        UNIQUE (coffee_shop_id, order_number)
);

CREATE INDEX idx_orders_coffee_shop_created
    ON orders (coffee_shop_id, created_at DESC, id DESC);

CREATE INDEX idx_orders_coffee_shop_status
    ON orders (coffee_shop_id, status, created_at DESC);

CREATE TABLE order_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    coffee_shop_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,

    product_name VARCHAR(150) NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL,
    unit_cost NUMERIC(14, 4),
    quantity INTEGER NOT NULL,
    subtotal NUMERIC(15, 2) NOT NULL,

    stock_tracked BOOLEAN NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_order_items_coffee_shop
        FOREIGN KEY (coffee_shop_id)
        REFERENCES coffee_shops (id),

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id, coffee_shop_id)
        REFERENCES orders (id, coffee_shop_id),

    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id, coffee_shop_id)
        REFERENCES products (id, coffee_shop_id),

    CONSTRAINT ck_order_items_product_name_not_blank
        CHECK (BTRIM(product_name) <> ''),

    CONSTRAINT ck_order_items_unit_price_positive
        CHECK (unit_price > 0),

    CONSTRAINT ck_order_items_unit_cost_non_negative
        CHECK (unit_cost IS NULL OR unit_cost >= 0),

    CONSTRAINT ck_order_items_quantity_range
        CHECK (quantity BETWEEN 1 AND 999),

    CONSTRAINT ck_order_items_subtotal
        CHECK (subtotal = unit_price * quantity),

    CONSTRAINT uq_order_items_order_product
        UNIQUE (order_id, product_id)
);

CREATE INDEX idx_order_items_product
    ON order_items (coffee_shop_id, product_id);

ALTER TABLE stock_movements
    ADD CONSTRAINT fk_stock_movements_order
        FOREIGN KEY (order_id, coffee_shop_id)
        REFERENCES orders (id, coffee_shop_id);
