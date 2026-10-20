package com.example.shop.dto;

import com.example.shop.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// JSON body for paying an order, e.g. { "orderId": 7, "amount": 4067.97, "method": "CARD" }
// The amount must exactly match the order's total.
public record PaymentCreateRequest(

        @NotNull(message = "orderId is required")
        Long orderId,

        // Matches the database column NUMERIC(12, 2): up to 10 digits before the dot, 2 after
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.00", message = "amount must not be negative")
        @Digits(integer = 10, fraction = 2, message = "amount must have at most 10 digits before the dot and 2 after")
        BigDecimal amount,

        @NotNull(message = "method is required (CARD, CASH or UPI)")
        PaymentMethod method
) {
}
