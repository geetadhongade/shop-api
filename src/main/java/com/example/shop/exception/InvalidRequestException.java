package com.example.shop.exception;

// Thrown when the request itself is wrong in a way the @Valid checks can't see,
// e.g. "product 1 is listed twice in the order" (returned as 400 Bad Request)
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
