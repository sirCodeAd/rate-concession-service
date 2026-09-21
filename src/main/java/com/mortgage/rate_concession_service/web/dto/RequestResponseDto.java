package com.mortgage.rate_concession_service.web.dto;

import com.mortgage.rate_concession_service.domain.PricingExceptionRequest;

import java.time.Instant;
import java.util.UUID;

/** Response representation of a {@link PricingExceptionRequest}. */
public record RequestResponseDto(
        UUID id,
        String applicationId,
        int requestedDiscountBps,
        String reason,
        String status,
        String createdByUserId,
        Instant createdAt,
        String decidedByUserId,
        Instant decidedAt,
        String decisionReason,
        long version) {

    public static RequestResponseDto from(PricingExceptionRequest r) {
        return new RequestResponseDto(
                r.getId(),
                r.getApplicationId(),
                r.getRequestedDiscountBps(),
                r.getReason(),
                r.getStatus().name(),
                r.getCreatedByUserId(),
                r.getCreatedAt(),
                r.getDecidedByUserId(),
                r.getDecidedAt(),
                r.getDecisionReason(),
                r.getVersion());
    }
}
