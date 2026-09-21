package com.mortgage.rate_concession_service.service;

import com.mortgage.rate_concession_service.exception.BadRequestException;
import tools.jackson.databind.ObjectMapper;
import com.mortgage.rate_concession_service.domain.AppUser;
import com.mortgage.rate_concession_service.domain.EventType;
import com.mortgage.rate_concession_service.domain.IdempotencyRecord;
import com.mortgage.rate_concession_service.domain.PricingExceptionRequest;
import com.mortgage.rate_concession_service.domain.RequestHistoryEvent;
import com.mortgage.rate_concession_service.domain.RequestStatus;
import com.mortgage.rate_concession_service.exception.ConflictException;
import com.mortgage.rate_concession_service.exception.NotFoundException;
import com.mortgage.rate_concession_service.repository.IdempotencyRecordRepository;
import com.mortgage.rate_concession_service.repository.MortgageApplicationRepository;
import com.mortgage.rate_concession_service.repository.PricingExceptionRequestRepository;
import com.mortgage.rate_concession_service.repository.RequestHistoryEventRepository;
import com.mortgage.rate_concession_service.web.dto.ApprovedDiscountResponseDto;
import com.mortgage.rate_concession_service.web.dto.CreateRequestDto;
import com.mortgage.rate_concession_service.web.dto.DecisionRequestDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core use cases for pricing exception requests: creation (with idempotency), review decisions,
 * withdrawal, and read paths. Decision/withdrawal transitions rely on a conditional atomic update
 * (see {@link PricingExceptionRequestRepository#transitionIfPending}) so that concurrent attempts
 * on the same request can only ever have one winner.
 */
@Service
public class PricingExceptionRequestService {

    private final PricingExceptionRequestRepository requestRepository;
    private final RequestHistoryEventRepository historyRepository;
    private final MortgageApplicationRepository applicationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final HashUtil hashUtil;
    private final ObjectMapper objectMapper;
    private final IdempotentRequestCreator idempotentRequestCreator;

    public PricingExceptionRequestService(
            PricingExceptionRequestRepository requestRepository,
            RequestHistoryEventRepository historyRepository,
            MortgageApplicationRepository applicationRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            HashUtil hashUtil,
            ObjectMapper objectMapper,
            IdempotentRequestCreator idempotentRequestCreator) {
        this.requestRepository = requestRepository;
        this.historyRepository = historyRepository;
        this.applicationRepository = applicationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.hashUtil = hashUtil;
        this.objectMapper = objectMapper;
        this.idempotentRequestCreator = idempotentRequestCreator;
    }

    public record IdempotentResult(int httpStatus, String responseBodyJson) {
    }

    /**
     * Handles create-with-idempotency. Not itself transactional: it orchestrates a fast-path
     * lookup, a REQUIRES_NEW transactional create, and a fallback re-read if two truly concurrent
     * requests race on the same brand-new key (detected via the idempotency_key PK/unique
     * constraint).
     */
    public IdempotentResult createRequest(String idempotencyKey, CreateRequestDto dto, AppUser currentUser) {
        String normalizedBody = normalize(dto);
        String hash = hashUtil.sha256Hex(normalizedBody);

        Optional<IdempotencyRecord> existing =
                idempotencyRecordRepository.findByManagerIdAndIdempotencyKey(currentUser.getId(), idempotencyKey);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), hash);
        }

        try {
            return idempotentRequestCreator.create(idempotencyKey, hash, dto, currentUser);
        } catch (DataIntegrityViolationException raceLost) {
            IdempotencyRecord winner = idempotencyRecordRepository
                    .findByManagerIdAndIdempotencyKey(currentUser.getId(), idempotencyKey)
                    .orElseThrow(() -> raceLost);
            return replayOrConflict(winner, hash);
        }
    }

    private IdempotentResult replayOrConflict(IdempotencyRecord record, String hash) {
        if (!record.getRequestBodyHash().equals(hash)) {
            throw new ConflictException("Idempotency-Key already used with a different request body");
        }
        return new IdempotentResult(record.getHttpStatus(), record.getResponseBody());
    }

    private String normalize(CreateRequestDto dto) {
        return writeJson(new CreateRequestDto(dto.applicationId(), dto.requestedDiscountBps(), dto.reason()));
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize response", e);
        }
    }

    @Transactional(readOnly = true)
    public PricingExceptionRequest getOrThrow(UUID id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pricing exception request not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<RequestHistoryEvent> getHistory(UUID id) {
        getOrThrow(id);
        return historyRepository.findByRequestIdOrderByTimestampAsc(id);
    }

    @Transactional(readOnly = true)
    public List<PricingExceptionRequest> list(String applicationId, RequestStatus status) {
        if (applicationId != null && status != null) {
            return requestRepository.findByApplicationIdAndStatusOrderByCreatedAtDesc(applicationId, status);
        } else if (applicationId != null) {
            return requestRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        } else if (status != null) {
            return requestRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return requestRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public PricingExceptionRequest decide(UUID id, DecisionRequestDto dto, AppUser reviewer) {
        // Ensure the request exists at all, to distinguish 404 from 409.
        getOrThrow(id);

        RequestStatus newStatus = dto.decision() == DecisionRequestDto.Decision.APPROVE
                ? RequestStatus.APPROVED
                : RequestStatus.DECLINED;
        Instant decidedAt = Instant.now();

        int updated = requestRepository.transitionIfPending(id, newStatus, reviewer.getId(), decidedAt, dto.reason());
        if (updated == 0) {
            throw new ConflictException("Request is no longer PENDING (already decided or withdrawn)");
        }

        EventType eventType = newStatus == RequestStatus.APPROVED ? EventType.APPROVED : EventType.DECLINED;
        historyRepository.save(new RequestHistoryEvent(id, eventType, reviewer.getId(), dto.reason()));

        return getOrThrow(id);
    }

    @Transactional
    public PricingExceptionRequest withdraw(UUID id, String reason, AppUser currentUser) {
        // Any authenticated relationship manager may withdraw a pending request, not only its
        // creator - mirroring how any REVIEWER (not a specifically assigned one) may decide on a
        // request. Continuity (e.g. covering for an absent colleague) outweighs the narrower
        // ownership check; the actor is still recorded in the history event for accountability.
        getOrThrow(id);

        Instant decidedAt = Instant.now();
        int updated = requestRepository.transitionIfPending(
                id, RequestStatus.WITHDRAWN, currentUser.getId(), decidedAt, reason);
        if (updated == 0) {
            throw new ConflictException("Request is no longer PENDING (already decided or withdrawn)");
        }

        historyRepository.save(new RequestHistoryEvent(id, EventType.WITHDRAWN, currentUser.getId(), reason));

        return getOrThrow(id);
    }

    @Transactional(readOnly = true)
    public ApprovedDiscountResponseDto approvedDiscount(String applicationId) {
        if(applicationId == null || applicationId.isBlank()) {
            throw new BadRequestException("Application id is null or blank");
        }
        if (!applicationRepository.existsById(applicationId)) {
            throw new NotFoundException("Mortgage application not found: " + applicationId);
        }
        List<PricingExceptionRequest> approved = requestRepository
                .findByApplicationIdAndStatusOrderByDecidedAtDesc(applicationId, RequestStatus.APPROVED);
        if (approved.isEmpty()) {
            return ApprovedDiscountResponseDto.none(applicationId);
        }
        PricingExceptionRequest winner = approved.getFirst();
        return new ApprovedDiscountResponseDto(
                applicationId,
                winner.getRequestedDiscountBps(),
                true,
                winner.getDecidedByUserId(),
                winner.getDecidedAt(),
                winner.getId().toString());
    }
}
