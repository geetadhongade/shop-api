package com.example.shop.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// The message sent to Kafka when an order is placed (stored as JSON in outbox_event.payload), e.g.
// { "eventId": "3f9c2b1e-...", "eventType": "ORDER_PLACED", "schemaVersion": 1,
//   "occurredAt": "2026-10-21T14:32:05.123Z", "orderId": 12, "customerId": 6,
//   "customerFirstName": "Bob", "customerPhone": "+1 555 0100",
//   "totalAmount": 241.00, "currency": "USD", "itemCount": 2 }
// Only what the SMS needs - no email, address or item list (Kafka keeps messages for days).
public record OrderPlacedEvent(
        UUID eventId,
        String eventType,
        // Bump this if the message's fields ever change, so consumers can tell old from new
        int schemaVersion,
        // When the order was placed (UTC)
        Instant occurredAt,
        Long orderId,
        Long customerId,
        String customerFirstName,
        String customerPhone,
        BigDecimal totalAmount,
        String currency,
        // Total quantity of all items, e.g. 2 headphones + 3 t-shirts = 5
        int itemCount
) {
}
