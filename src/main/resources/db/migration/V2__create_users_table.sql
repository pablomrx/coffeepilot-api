CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    establishment_id BIGINT NOT NULL,

    name VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL,

    password_hash VARCHAR(255),

    role VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    email_confirmed_at TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ,
    token_version INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_users_establishment
        FOREIGN KEY (establishment_id)
        REFERENCES establishments (id),

    CONSTRAINT ck_users_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT ck_users_email_normalized
        CHECK (email = LOWER(BTRIM(email))),

    CONSTRAINT ck_users_password_hash_not_blank
        CHECK (password_hash IS NULL OR BTRIM(password_hash) <> ''),

    CONSTRAINT ck_users_confirmed_has_password
        CHECK (password_hash IS NOT NULL OR email_confirmed_at IS NULL),

    CONSTRAINT ck_users_token_version
        CHECK (token_version >= 0),

    CONSTRAINT ck_users_role
        CHECK (role IN ('OWNER', 'MANAGER', 'ATTENDANT', 'STOCKIST')),

    CONSTRAINT uq_users_email
        UNIQUE (email),

    CONSTRAINT uq_users_id_establishment
        UNIQUE (id, establishment_id)
);

CREATE INDEX idx_users_establishment_id
    ON users (establishment_id);


CREATE TABLE user_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,

    token_hash VARCHAR(64) NOT NULL,

    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users (id),

    CONSTRAINT ck_user_tokens_type
        CHECK (type IN ('EMAIL_CONFIRMATION', 'PASSWORD_RESET', 'EMPLOYEE_INVITE')),

    CONSTRAINT ck_user_tokens_expiry
        CHECK (expires_at > created_at),

    CONSTRAINT ck_user_tokens_used_xor_revoked
        CHECK (used_at IS NULL OR revoked_at IS NULL),

    CONSTRAINT ck_user_tokens_hash_format
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),

    CONSTRAINT uq_user_tokens_hash
        UNIQUE (token_hash)
);

CREATE UNIQUE INDEX uq_user_tokens_active
    ON user_tokens (user_id, type)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE INDEX idx_user_tokens_user_id
    ON user_tokens (user_id);

CREATE INDEX idx_user_tokens_expires_at
    ON user_tokens (expires_at);


CREATE TABLE email_usage (
    day DATE PRIMARY KEY,
    reserved INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT ck_email_usage_reserved
        CHECK (reserved >= 0)
);
