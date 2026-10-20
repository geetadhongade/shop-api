package com.example.shop.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerCreateRequestTest {

    // The password must never show up if this object is printed or logged
    @Test
    void toString_hidesPassword() {
        CustomerCreateRequest request = new CustomerCreateRequest("Anna", "Smith", "anna@example.com", "5550100",
                null, null, null, null, null, "anna.smith", "s3cretPass");

        assertThat(request.toString())
                .contains("anna.smith")
                .contains("****")
                .doesNotContain("s3cretPass");
    }
}
