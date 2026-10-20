package com.example.shop.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// An order's total converted to another currency, e.g.
// { "orderId": 7, "totalAmount": 4067.97, "baseCurrency": "USD", "currency": "EUR",
//   "rate": 0.89087, "convertedTotal": 3624.03, "rateDate": "2026-10-19" }
public record OrderTotalResponse(
        Long orderId,
        BigDecimal totalAmount,
        String baseCurrency,
        String currency,
        BigDecimal rate,
        BigDecimal convertedTotal,
        LocalDate rateDate
) {
}
