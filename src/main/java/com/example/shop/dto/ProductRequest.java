package com.example.shop.dto;

import com.example.shop.entity.ProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// JSON body for creating or updating a product, e.g.
// { "name": "Laptop", "type": "ELECTRONICS", "price": 899.99 }
public record ProductRequest(

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotNull(message = "type is required (ELECTRONICS, CLOTHING, FOOD, COSMETICS or DRUGS)")
        ProductType type,

        // Matches the database column NUMERIC(10, 2): up to 8 digits before the dot, 2 after
        @NotNull(message = "price is required")
        @DecimalMin(value = "0.00", message = "price must not be negative")
        @Digits(integer = 8, fraction = 2, message = "price must have at most 8 digits before the dot and 2 after")
        BigDecimal price
) {
}
