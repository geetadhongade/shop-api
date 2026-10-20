CREATE TABLE customer_credentials (
    id             BIGSERIAL PRIMARY KEY,

    -- UNIQUE here means each customer can have only one login
    customer_id    BIGINT NOT NULL UNIQUE REFERENCES customer (id),

    username       VARCHAR(50)  NOT NULL UNIQUE,

    -- BCrypt-scrambled password (BCrypt output is 60 characters). Never the real password.
    password_hash  VARCHAR(100) NOT NULL,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
