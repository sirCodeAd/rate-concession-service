package com.mortgage.rate_concession_service.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link IdempotencyRecord}: idempotency is scoped per caller
 * (managerId), so the same key value may be reused independently by different relationship
 * managers without colliding.
 */
public class IdempotencyRecordId implements Serializable {

    private String managerId;
    private String idempotencyKey;

    public IdempotencyRecordId() {
        // JPA
    }

    public IdempotencyRecordId(String managerId, String idempotencyKey) {
        this.managerId = managerId;
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyRecordId that)) return false;
        return Objects.equals(managerId, that.managerId) && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(managerId, idempotencyKey);
    }
}
