CREATE TABLE order_sequences (
    establishment_id BIGINT PRIMARY KEY,

    last_number BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_order_sequences_establishment
        FOREIGN KEY (establishment_id)
        REFERENCES establishments (id),

    CONSTRAINT ck_order_sequences_last_number_non_negative
        CHECK (last_number >= 0)
);

CREATE TABLE orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    establishment_id BIGINT NOT NULL,
    order_number BIGINT NOT NULL,

    status VARCHAR(20) NOT NULL,

    table_reference VARCHAR(50),
    notes VARCHAR(500),

    total NUMERIC(15, 2) NOT NULL,

    created_by BIGINT NOT NULL,

    started_at TIMESTAMPTZ,
    started_by BIGINT,

    completed_at TIMESTAMPTZ,
    completed_by BIGINT,

    cancelled_at TIMESTAMPTZ,
    cancelled_by BIGINT,
    cancelled_from_status VARCHAR(20),
    cancellation_reason VARCHAR(500),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_orders_establishment
        FOREIGN KEY (establishment_id)
        REFERENCES establishments (id),

    CONSTRAINT fk_orders_created_by
        FOREIGN KEY (created_by, establishment_id)
        REFERENCES users (id, establishment_id),

    CONSTRAINT fk_orders_started_by
        FOREIGN KEY (started_by, establishment_id)
        REFERENCES users (id, establishment_id),

    CONSTRAINT fk_orders_completed_by
        FOREIGN KEY (completed_by, establishment_id)
        REFERENCES users (id, establishment_id),

    CONSTRAINT fk_orders_cancelled_by
        FOREIGN KEY (cancelled_by, establishment_id)
        REFERENCES users (id, establishment_id),

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

    CONSTRAINT ck_orders_started
        CHECK (
            (
                status = 'PENDING'
                AND started_at IS NULL
                AND started_by IS NULL
            )
            OR (
                status IN ('IN_PREPARATION', 'COMPLETED')
                AND started_at IS NOT NULL
                AND started_by IS NOT NULL
            )
            OR (
                status = 'CANCELLED'
                AND (cancelled_from_status = 'IN_PREPARATION')
                = (started_at IS NOT NULL)
                AND (started_at IS NULL) = (started_by IS NULL)
            )
        ),

    CONSTRAINT ck_orders_completed_at
        CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),

    CONSTRAINT ck_orders_completed_by
        CHECK ((status = 'COMPLETED') = (completed_by IS NOT NULL)),

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

    CONSTRAINT uq_orders_id_establishment
        UNIQUE (id, establishment_id),

    CONSTRAINT uq_orders_establishment_number
        UNIQUE (establishment_id, order_number)
);

CREATE INDEX idx_orders_establishment_created
    ON orders (establishment_id, created_at DESC, id DESC);

CREATE INDEX idx_orders_establishment_status
    ON orders (establishment_id, status, created_at DESC);

CREATE TABLE order_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    establishment_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,

    product_name VARCHAR(150) NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL,
    unit_cost NUMERIC(14, 4),
    quantity INTEGER NOT NULL,
    subtotal NUMERIC(15, 2) NOT NULL,

    stock_tracked BOOLEAN NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_order_items_establishment
        FOREIGN KEY (establishment_id)
        REFERENCES establishments (id),

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id, establishment_id)
        REFERENCES orders (id, establishment_id),

    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id, establishment_id)
        REFERENCES products (id, establishment_id),

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
    ON order_items (establishment_id, product_id);

ALTER TABLE stock_movements
    ADD CONSTRAINT fk_stock_movements_order
    FOREIGN KEY (order_id, establishment_id)
    REFERENCES orders (id, establishment_id);
