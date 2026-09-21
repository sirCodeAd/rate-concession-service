package com.mortgage.rate_concession_service.exception;

/** Thrown when a request header required for an endpoint is missing. Mapped to HTTP 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
