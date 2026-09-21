package com.mortgage.rate_concession_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Stores the outcome of a previously-processed {@code Idempotency-Key} for POST /api/requests, so
 * that retried creates return the original response instead of creating duplicate rows.
 *
 * <p>The key is scoped per caller: {@code (managerId, idempotencyKey)} is the primary key, so two
 * different relationship managers may reuse the same key value independently without colliding
 * or seeing each other's responses.
 */
@Entity
@Table(name = "idempotency_record")
@IdClass(IdempotencyRecordId.class)
public class IdempotencyRecord {

    @Id
    @Column(name = "manager_id")
    private String managerId;

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_body_hash", nullable = false)
    private String requestBodyHash;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "response_body", nullable = false, length = 4000)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // JPA
    }

    public IdempotencyRecord(
            String idempotencyKey, String managerId, String requestBodyHash, int httpStatus, String responseBody) {
        this.idempotencyKey = idempotencyKey;
        this.managerId = managerId;
        this.requestBodyHash = requestBodyHash;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getManagerId() {
        return managerId;
    }

    public String getRequestBodyHash() {
        return requestBodyHash;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
