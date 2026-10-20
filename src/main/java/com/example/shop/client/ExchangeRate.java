package com.example.shop.client;

import java.math.BigDecimal;
import java.time.LocalDate;

// One exchange rate, e.g. 1 USD = 0.89087 EUR on 2026-10-19
public record ExchangeRate(
        String fromCurrency,
        String toCurrency,
        BigDecimal rate,
        LocalDate date
) {
}
