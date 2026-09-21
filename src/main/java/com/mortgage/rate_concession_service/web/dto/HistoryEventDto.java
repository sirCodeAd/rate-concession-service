package com.mortgage.rate_concession_service.web.dto;

import com.mortgage.rate_concession_service.domain.RequestHistoryEvent;

import java.time.Instant;

/** Response representation of a single {@link RequestHistoryEvent} audit row. */
public record HistoryEventDto(
        Long id, String requestId, String eventType, String actorUserId, Instant timestamp, String notes) {

    public static HistoryEventDto from(RequestHistoryEvent e) {
        return new HistoryEventDto(
                e.getId(), e.getRequestId().toString(), e.getEventType().name(), e.getActorUserId(), e.getTimestamp(),
                e.getNotes());
    }
}
