package com.example.shop.entity;

// Must match the allowed values in the payment table's CHECK rule (V6 script)
public enum PaymentMethod {
    CARD,
    CASH,
    UPI
}
