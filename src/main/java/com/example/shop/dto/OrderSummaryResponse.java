package com.example.shop.dto;

import com.example.shop.entity.Order;
import com.example.shop.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// A short version of an order (no items), used in the order list.
// Use GET /api/orders/{id} to see the items.
public record OrderSummaryResponse(
        Long id,
        Long customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getCustomer().getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
