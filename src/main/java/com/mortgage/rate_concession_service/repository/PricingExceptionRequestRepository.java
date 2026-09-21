package com.mortgage.rate_concession_service.repository;

import com.mortgage.rate_concession_service.domain.PricingExceptionRequest;
import com.mortgage.rate_concession_service.domain.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PricingExceptionRequestRepository extends JpaRepository<PricingExceptionRequest, UUID> {

    List<PricingExceptionRequest> findByApplicationIdAndStatusOrderByCreatedAtDesc(
            String applicationId, RequestStatus status);

    List<PricingExceptionRequest> findByApplicationIdOrderByCreatedAtDesc(String applicationId);

    List<PricingExceptionRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status);

    List<PricingExceptionRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByApplicationIdAndStatus(String applicationId, RequestStatus status);

    List<PricingExceptionRequest> findByApplicationIdAndStatusOrderByDecidedAtDesc(
            String applicationId, RequestStatus status);

    /**
     * Atomically transitions a PENDING request to a terminal status. Returns the number of rows
     * affected (0 or 1); a caller-side check of that count is what guarantees only one of two
     * concurrent decision/withdrawal attempts on the same request can ever succeed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PricingExceptionRequest r
            set r.status = :newStatus,
                r.decidedByUserId = :decidedByUserId,
                r.decidedAt = :decidedAt,
                r.decisionReason = :decisionReason
            where r.id = :id and r.status = com.mortgage.rate_concession_service.domain.RequestStatus.PENDING
            """)
    int transitionIfPending(
            @Param("id") UUID id,
            @Param("newStatus") RequestStatus newStatus,
            @Param("decidedByUserId") String decidedByUserId,
            @Param("decidedAt") Instant decidedAt,
            @Param("decisionReason") String decisionReason);
}
