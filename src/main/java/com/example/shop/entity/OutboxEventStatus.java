package com.example.shop.entity;

// Must match the allowed values in the outbox_event table's CHECK rule (V8 script)
public enum OutboxEventStatus {
    // Saved, not yet confirmed by Kafka
    NEW,
    // Kafka confirmed it received the event
    PUBLISHED
}
