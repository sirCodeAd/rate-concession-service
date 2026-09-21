package com.mortgage.rate_concession_service.domain;

/** Lifecycle status of a {@link PricingExceptionRequest}. PENDING is the only non-terminal state. */
public enum RequestStatus {
    PENDING,
    APPROVED,
    DECLINED,
    WITHDRAWN
}
