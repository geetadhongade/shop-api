package com.example.shop.entity;

// Must match the allowed values in the outbox_event table's CHECK rule (V8 script)
public enum OutboxEventType {
    ORDER_PLACED
}
