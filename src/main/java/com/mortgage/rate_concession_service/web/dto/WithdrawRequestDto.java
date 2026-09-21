package com.mortgage.rate_concession_service.web.dto;

import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/requests/{id}/withdraw}; both fields optional. */
public record WithdrawRequestDto(
        @Size(max = 2000, message = "reason must be at most 2000 characters") String reason) {
}
