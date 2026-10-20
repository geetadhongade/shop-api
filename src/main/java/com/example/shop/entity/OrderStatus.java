package com.example.shop.entity;

// Must match the allowed values in the orders table's CHECK rule (V4 script)
public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    CANCELLED
}
