package com.example.shop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// JSON body for updating a customer's details. Every field is optional:
// only the fields that are sent are changed, missing fields keep their current value.
// e.g. { "city": "Dallas" } changes only the city.
// Username and password can NOT be changed here (they will get their own endpoint later).
public record CustomerUpdateRequest(

        // ".*\\S.*" = must contain at least one non-space character (only checked when the field is sent)
        @Pattern(regexp = ".*\\S.*", message = "firstName must not be blank")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        String firstName,

        @Pattern(regexp = ".*\\S.*", message = "lastName must not be blank")
        @Size(max = 100, message = "lastName must be at most 100 characters")
        String lastName,

        // @Email alone allows "", so the blank check is needed too
        @Pattern(regexp = ".*\\S.*", message = "email must not be blank")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @Pattern(regexp = "^[0-9+()\\- ]{5,20}$", message = "phone must be 5-20 characters: digits, spaces, + - ( )")
        String phone,

        @Size(max = 255, message = "street must be at most 255 characters") String street,
        @Size(max = 100, message = "city must be at most 100 characters") String city,
        @Size(max = 100, message = "state must be at most 100 characters") String state,
        @Size(max = 20, message = "zipCode must be at most 20 characters") String zipCode,
        @Size(max = 100, message = "country must be at most 100 characters") String country
) {
}
