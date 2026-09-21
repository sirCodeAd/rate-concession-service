package com.mortgage.rate_concession_service.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/requests/{id}/decision}. */
public record DecisionRequestDto(
        @NotNull(message = "decision is required") Decision decision,
        @Size(max = 2000, message = "reason must be at most 2000 characters") String reason) {

    public enum Decision {
        APPROVE,
        DECLINE
    }
}
