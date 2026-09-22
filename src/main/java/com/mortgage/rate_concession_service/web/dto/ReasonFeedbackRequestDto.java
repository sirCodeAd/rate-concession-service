package com.mortgage.rate_concession_service.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/requests/reason-feedback} unlike on create, since a draft may be checked
 * before an application is chosen. Nothing submitted here is ever persisted.
 */
public record ReasonFeedbackRequestDto(
        @Size(max = 64, message = "applicationId must be at most 64 characters")
        String applicationId,
        @Min(value = 1, message = "requestedDiscountBps must be greater than 0")
        @Max(value = 500, message = "requestedDiscountBps must be at most 500")
        int requestedDiscountBps,
        @NotBlank(message = "reason is required")
        @Size(max = 2000, message = "reason must be at most 2000 characters")
        String reason) {
}
