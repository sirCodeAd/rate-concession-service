package com.mortgage.rate_concession_service.web.dto;

import java.time.Instant;
import java.util.List;

/** Consistent JSON error shape returned by all error paths. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors) {

    public record FieldErrorDetail(String field, String message) {
    }

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ErrorResponse validation(
            int status, String error, String message, String path, List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
    }
}
