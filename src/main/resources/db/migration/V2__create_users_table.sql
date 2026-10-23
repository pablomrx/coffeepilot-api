CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    coffee_shop_id BIGINT NOT NULL,

    name VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    role VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_users_coffee_shop
        FOREIGN KEY (coffee_shop_id)
        REFERENCES coffee_shops (id),

    CONSTRAINT ck_users_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT ck_users_email_normalized
        CHECK (email = LOWER(BTRIM(email))),

    CONSTRAINT ck_users_password_hash_not_blank
        CHECK (BTRIM(password_hash) <> ''),

    CONSTRAINT ck_users_role
        CHECK (role IN ('OWNER', 'MANAGER', 'ATTENDANT', 'STOCKIST')),

    CONSTRAINT uq_users_email
        UNIQUE (email)
);

CREATE INDEX idx_users_coffee_shop_id
    ON users (coffee_shop_id);
