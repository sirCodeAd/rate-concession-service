package com.mortgage.rate_concession_service.web.dto;

import java.time.Instant;

/** Response for {@code GET /api/applications/{applicationId}/approved-discount}. */
public record ApprovedDiscountResponseDto(
        String applicationId,
        Integer discountBps,
        boolean hasApprovedDiscount,
        String decidedByUserId,
        Instant decidedAt,
        String requestId) {

    public static ApprovedDiscountResponseDto none(String applicationId) {
        return new ApprovedDiscountResponseDto(applicationId, null, false, null, null, null);
    }
}
