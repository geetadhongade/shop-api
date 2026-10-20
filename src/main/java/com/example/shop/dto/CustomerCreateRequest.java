package com.example.shop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// JSON body for signing up a new customer, e.g.
// { "firstName": "Anna", "lastName": "Smith", "email": "anna@example.com", "phone": "+1 555-0100",
//   "street": "1 Main St", "city": "Austin", "state": "TX", "zipCode": "73301", "country": "USA",
//   "username": "anna.smith", "password": "s3cretPass" }
public record CustomerCreateRequest(

        @NotBlank(message = "firstName is required")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 100, message = "lastName must be at most 100 characters")
        String lastName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        // Digits, spaces, +, -, ( and ) only, e.g. "+1 (555) 010-0100"
        @NotBlank(message = "phone is required")
        @Pattern(regexp = "^[0-9+()\\- ]{5,20}$", message = "phone must be 5-20 characters: digits, spaces, + - ( )")
        String phone,

        // Address fields are optional
        @Size(max = 255, message = "street must be at most 255 characters") String street,
        @Size(max = 100, message = "city must be at most 100 characters") String city,
        @Size(max = 100, message = "state must be at most 100 characters") String state,
        @Size(max = 20, message = "zipCode must be at most 20 characters") String zipCode,
        @Size(max = 100, message = "country must be at most 100 characters") String country,

        @NotBlank(message = "username is required")
        @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$", message = "username must be 3-50 characters: letters, digits, . _ -")
        String username,

        // BCrypt only uses the first 72 characters, so we don't allow longer passwords
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be 8-72 characters")
        String password
) {

    // Never print the password, e.g. if this object ends up in a log line
    @Override
    public String toString() {
        return "CustomerCreateRequest[username=" + username + ", password=****]";
    }
}
