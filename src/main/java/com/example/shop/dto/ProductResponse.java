package com.example.shop.dto;

import com.example.shop.entity.Product;
import com.example.shop.entity.ProductType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// JSON returned to the caller for a product
public record ProductResponse(
        Long id,
        String name,
        ProductType type,
        BigDecimal price,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    // Copies the fields from the database entity into the response
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getType(),
                product.getPrice(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
