package com.example.shop.dto;

import com.example.shop.entity.Payment;
import com.example.shop.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// JSON returned to the caller for a payment. createdAt is the payment date.
public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentMethod method,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
