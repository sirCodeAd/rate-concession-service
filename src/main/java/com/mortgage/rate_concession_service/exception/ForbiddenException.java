package com.mortgage.rate_concession_service.exception;

/** Thrown when an authenticated caller lacks the role/ownership required for an action. Mapped to HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
