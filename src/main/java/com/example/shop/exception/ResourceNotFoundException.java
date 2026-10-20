package com.example.shop.exception;

// Thrown when something we look up by ID doesn't exist (returned as 404 Not Found)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
