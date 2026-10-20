CREATE TABLE product (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,

    -- Only these product types are allowed
    type        VARCHAR(20) NOT NULL
                CHECK (type IN ('ELECTRONICS', 'CLOTHING', 'FOOD', 'COSMETICS', 'DRUGS')),

    -- Exact decimal with 2 places (e.g. 19.99), so money is never rounded wrong
    price       NUMERIC(10, 2) NOT NULL CHECK (price >= 0),

    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
