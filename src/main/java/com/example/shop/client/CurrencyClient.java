package com.example.shop.client;

import com.example.shop.exception.InvalidRequestException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;

// Calls the Frankfurter exchange-rate API (https://frankfurter.dev).
// Only currency codes are sent - never any customer or order data.
@Component
public class CurrencyClient {

    private static final Logger log = LoggerFactory.getLogger(CurrencyClient.class);

    private final RestClient frankfurterRestClient;
    private final String baseCurrency;

    public CurrencyClient(RestClient frankfurterRestClient, @Value("${shop.currency.base}") String baseCurrency) {
        this.frankfurterRestClient = frankfurterRestClient;
        this.baseCurrency = baseCurrency;
    }

    // The currency our prices are stored in (USD)
    public String getBaseCurrency() {
        return baseCurrency;
    }

    // Exchange rate from our base currency (USD) to targetCurrency, e.g. "EUR".
    // Settings for all three are under "currency" in application.properties:
    //   @Retry          - retries network problems / timeouts / 5xx (each attempt goes through the two below)
    //   @CircuitBreaker - stops calling Frankfurter for a while if it keeps failing (503 right away)
    //   @Bulkhead       - at most 10 calls to Frankfurter at the same time
    @Retry(name = "currency")
    @CircuitBreaker(name = "currency")
    @Bulkhead(name = "currency")
    public ExchangeRate getRateFromBase(String targetCurrency) {
        // Same currency: no need to call anyone
        if (targetCurrency.equals(baseCurrency)) {
            return new ExchangeRate(baseCurrency, targetCurrency, BigDecimal.ONE, LocalDate.now());
        }

        log.info("Calling Frankfurter for {} -> {}", baseCurrency, targetCurrency);
        FrankfurterResponse response;
        try {
            response = frankfurterRestClient.get()
                    .uri("/latest?base={base}&symbols={target}", baseCurrency, targetCurrency)
                    .retrieve()
                    .body(FrankfurterResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            // Frankfurter answers 404 for currencies it doesn't know
            throw new InvalidRequestException("Unknown currency '" + targetCurrency + "'");
        }

        if (response == null || response.rates() == null || !response.rates().containsKey(targetCurrency)) {
            throw new RestClientException("Frankfurter returned no rate for " + targetCurrency);
        }

        BigDecimal rate = response.rates().get(targetCurrency);
        log.info("Frankfurter rate {} -> {} = {} (date {})", baseCurrency, targetCurrency, rate, response.date());
        return new ExchangeRate(baseCurrency, targetCurrency, rate, response.date());
    }
}
