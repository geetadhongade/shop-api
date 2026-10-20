package com.example.shop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    // HTTP client for the Frankfurter exchange-rate API, with connect and read timeouts.
    // Without timeouts, a hanging Frankfurter could keep our request thread waiting forever.
    @Bean
    public RestClient frankfurterRestClient(
            @Value("${shop.currency.frankfurter.base-url}") String baseUrl,
            @Value("${shop.currency.frankfurter.connect-timeout}") Duration connectTimeout,
            @Value("${shop.currency.frankfurter.read-timeout}") Duration readTimeout) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
