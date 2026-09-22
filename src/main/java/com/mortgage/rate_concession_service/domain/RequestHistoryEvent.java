package com.mortgage.rate_concession_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_history_event")
public class RequestHistoryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "actor_user_id", nullable = false)
    private String actorUserId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(length = 2000)
    private String notes;

    protected RequestHistoryEvent() {
        // JPA
    }

    public RequestHistoryEvent(UUID requestId, EventType eventType, String actorUserId, String notes) {
        this.requestId = requestId;
        this.eventType = eventType;
        this.actorUserId = actorUserId;
        this.timestamp = Instant.now();
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getNotes() {
        return notes;
    }
}
