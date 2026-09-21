package com.mortgage.rate_concession_service.exception;

/** Thrown when caller authentication (X-User-Id / X-User-Role) fails. Mapped to HTTP 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
