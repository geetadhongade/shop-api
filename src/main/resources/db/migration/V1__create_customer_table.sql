CREATE TABLE customer (
    id          BIGSERIAL PRIMARY KEY,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    phone       VARCHAR(20)  NOT NULL,

    -- Address, split into parts
    street      VARCHAR(255),
    city        VARCHAR(100),
    state       VARCHAR(100),
    zip_code    VARCHAR(20),
    country     VARCHAR(100),

    -- Soft delete: the row is kept, but the customer is hidden from the API
    deleted     BOOLEAN NOT NULL DEFAULT FALSE,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
