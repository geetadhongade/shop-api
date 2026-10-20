CREATE TABLE payment (
    id          BIGSERIAL PRIMARY KEY,

    -- UNIQUE here means each order can have only one payment
    order_id    BIGINT NOT NULL UNIQUE REFERENCES orders (id),

    amount      NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),

    method      VARCHAR(10) NOT NULL
                CHECK (method IN ('CARD', 'CASH', 'UPI')),

    -- created_at is the payment date
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
