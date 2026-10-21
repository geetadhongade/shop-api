-- Events waiting to be sent to Kafka (the "transactional outbox").
-- A row is saved in the SAME transaction as the order, so an event is never lost
-- even if Kafka is down. A background publisher sends NEW rows and marks them PUBLISHED.
CREATE TABLE outbox_event (
    -- Also the sending order: oldest (lowest id) first
    id            BIGSERIAL PRIMARY KEY,

    -- Unique ID of this event, created once and reused on every re-send,
    -- so the consumer can recognise and skip duplicates
    event_id      UUID NOT NULL UNIQUE,

    event_type    VARCHAR(50) NOT NULL
                  CHECK (event_type IN ('ORDER_PLACED')),

    -- The order this event is about (also used as the Kafka message key)
    order_id      BIGINT NOT NULL REFERENCES orders (id),

    -- The exact JSON message body that will be sent to Kafka
    payload       JSONB NOT NULL,

    -- Request ID of the API call that created the event (passed on in Kafka headers)
    request_id    VARCHAR(64),

    status        VARCHAR(20) NOT NULL DEFAULT 'NEW'
                  CHECK (status IN ('NEW', 'PUBLISHED')),

    -- Failed send attempts so far, and the last error (helps when Kafka is down)
    attempts      INT NOT NULL DEFAULT 0,
    last_error    VARCHAR(500),

    -- When Kafka confirmed it received the event
    published_at  TIMESTAMPTZ,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- The publisher only looks for NEW rows; this index covers just those,
-- so it stays small and fast however many rows have been published
CREATE INDEX outbox_event_new_idx ON outbox_event (id) WHERE status = 'NEW';
