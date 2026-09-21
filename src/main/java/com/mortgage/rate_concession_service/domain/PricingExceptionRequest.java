package com.mortgage.rate_concession_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable-from-creation request for a discount (in basis points) off the standard rate on a
 * mortgage application. Deliberately has no update/edit endpoint: once created the discount and
 * reason can never change. If an RM wants different terms they must withdraw and create a new
 * request, ensuring any approval always applies to the exact request that was reviewed.
 */
@Entity
@Table(name = "pricing_exception_request")
public class PricingExceptionRequest {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private String applicationId;

    @Column(name = "requested_discount_bps", nullable = false)
    private int requestedDiscountBps;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    @Column(name = "created_by_user_id", nullable = false)
    private String createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_by_user_id")
    private String decidedByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", length = 2000)
    private String decisionReason;

    @Version
    private long version;

    protected PricingExceptionRequest() {
        // JPA
    }

    public PricingExceptionRequest(
            String applicationId, int requestedDiscountBps, String reason, String createdByUserId) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.requestedDiscountBps = requestedDiscountBps;
        this.reason = reason;
        this.createdByUserId = createdByUserId;
        this.createdAt = Instant.now();
        this.status = RequestStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public int getRequestedDiscountBps() {
        return requestedDiscountBps;
    }

    public String getReason() {
        return reason;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getDecidedByUserId() {
        return decidedByUserId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public long getVersion() {
        return version;
    }
}
