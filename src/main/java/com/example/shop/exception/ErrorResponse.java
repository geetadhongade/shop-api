package com.example.shop.exception;

import java.time.OffsetDateTime;
import java.util.Map;

// The JSON body returned for every error, e.g.
// { "timestamp": "...", "status": 404, "error": "Not Found", "message": "Product 5 not found", "fieldErrors": null }
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        // Only filled in for validation errors: field name -> what is wrong with it
        Map<String, String> fieldErrors
) {
}
