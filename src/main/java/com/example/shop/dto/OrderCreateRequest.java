package com.example.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// JSON body for placing an order, e.g.
// { "customerId": 6, "items": [ { "productId": 1, "quantity": 2 }, { "productId": 3, "quantity": 1 } ] }
// Prices are NOT sent by the caller: they are copied from the product table when the order is placed.
public record OrderCreateRequest(

        @NotNull(message = "customerId is required")
        Long customerId,

        // @NotNull on the item = no empty entries like [null]; @Valid = also check each item's productId and quantity
        @NotEmpty(message = "items must contain at least one product")
        List<@NotNull(message = "items must not contain empty entries") @Valid OrderItemRequest> items
) {
}
