package com.example.shop.dto;

import com.example.shop.entity.OrderItem;
import com.example.shop.entity.Product;
import com.example.shop.entity.ProductType;

import java.math.BigDecimal;

// One product in an order, as returned to the caller.
// unitPrice is the price at the time of purchase; lineTotal = unitPrice x quantity.
public record OrderItemResponse(
        Long productId,
        String productName,
        ProductType productType,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {

    public static OrderItemResponse from(OrderItem item) {
        Product product = item.getProduct();
        BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new OrderItemResponse(
                product.getId(),
                product.getName(),
                product.getType(),
                item.getQuantity(),
                item.getUnitPrice(),
                lineTotal
        );
    }
}
