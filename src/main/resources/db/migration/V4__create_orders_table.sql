-- Named "orders" because "order" is a reserved word in SQL (as in ORDER BY)
CREATE TABLE orders (
    id            BIGSERIAL PRIMARY KEY,
    customer_id   BIGINT NOT NULL REFERENCES customer (id),

    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'PAID', 'SHIPPED', 'CANCELLED')),

    -- Sum of (quantity x price) of all items in this order
    total_amount  NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),

    -- created_at is the date the order was placed
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
