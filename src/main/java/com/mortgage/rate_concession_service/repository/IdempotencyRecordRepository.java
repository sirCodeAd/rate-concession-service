package com.mortgage.rate_concession_service.repository;

import com.mortgage.rate_concession_service.domain.IdempotencyRecord;
import com.mortgage.rate_concession_service.domain.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {

    Optional<IdempotencyRecord> findByManagerIdAndIdempotencyKey(String managerId, String idempotencyKey);
}
