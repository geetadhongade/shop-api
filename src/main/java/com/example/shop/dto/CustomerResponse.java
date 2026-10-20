package com.example.shop.dto;

import com.example.shop.entity.Customer;

import java.time.OffsetDateTime;

// JSON returned to the caller for a customer. Never contains the password.
public record CustomerResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String street,
        String city,
        String state,
        String zipCode,
        String country,
        String username,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    // Copies the fields from the database entity (plus the username from customer_credentials)
    public static CustomerResponse from(Customer customer, String username) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getStreet(),
                customer.getCity(),
                customer.getState(),
                customer.getZipCode(),
                customer.getCountry(),
                username,
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
