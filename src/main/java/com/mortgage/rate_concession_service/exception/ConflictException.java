package com.mortgage.rate_concession_service.exception;

/**
 * Thrown when a state transition (decision/withdrawal) is attempted on a request that is no
 * longer PENDING, or when an Idempotency-Key is reused with a different request body. Mapped to
 * HTTP 409.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
