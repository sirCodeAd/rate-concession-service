package com.mortgage.rate_concession_service.exception;

/** Thrown when a requested entity (application, request) cannot be found. Mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
