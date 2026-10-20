package com.example.shop.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

// The JSON Frankfurter returns, e.g.
// { "amount": 1.0, "base": "USD", "date": "2026-10-19", "rates": { "EUR": 0.89087 } }
public record FrankfurterResponse(
        BigDecimal amount,
        String base,
        LocalDate date,
        Map<String, BigDecimal> rates
) {
}
