package com.mortgage.rate_concession_service.domain;

/** Type of state transition recorded in the append-only {@link RequestHistoryEvent} audit log. */
public enum EventType {
    CREATED,
    APPROVED,
    DECLINED,
    WITHDRAWN
}
