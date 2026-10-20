-- One row per product in an order
CREATE TABLE order_item (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES orders (id),
    product_id  BIGINT NOT NULL REFERENCES product (id),

    quantity    INTEGER NOT NULL CHECK (quantity >= 1),

    -- Product price at the time of purchase, so old orders don't change when the product price changes
    unit_price  NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0),

    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- The same product can't appear twice in one order (increase quantity instead)
    UNIQUE (order_id, product_id)
);
