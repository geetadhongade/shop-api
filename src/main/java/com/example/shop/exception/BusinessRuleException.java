package com.example.shop.exception;

// Thrown when a request breaks one of our rules, e.g. "email already taken",
// "product is in an order and can't be deleted" (returned as 409 Conflict)
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
