package com.mortgage.rate_concession_service.service;

import tools.jackson.databind.ObjectMapper;
import com.mortgage.rate_concession_service.domain.AppUser;
import com.mortgage.rate_concession_service.domain.EventType;
import com.mortgage.rate_concession_service.domain.IdempotencyRecord;
import com.mortgage.rate_concession_service.domain.MortgageApplication;
import com.mortgage.rate_concession_service.domain.PricingExceptionRequest;
import com.mortgage.rate_concession_service.domain.RequestHistoryEvent;
import com.mortgage.rate_concession_service.domain.RequestStatus;
import com.mortgage.rate_concession_service.exception.ConflictException;
import com.mortgage.rate_concession_service.exception.NotFoundException;
import com.mortgage.rate_concession_service.repository.IdempotencyRecordRepository;
import com.mortgage.rate_concession_service.repository.MortgageApplicationRepository;
import com.mortgage.rate_concession_service.repository.PricingExceptionRequestRepository;
import com.mortgage.rate_concession_service.repository.RequestHistoryEventRepository;
import com.mortgage.rate_concession_service.web.dto.CreateRequestDto;
import com.mortgage.rate_concession_service.web.dto.RequestResponseDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated in its own bean (rather than a method on {@link PricingExceptionRequestService}) so
 * that the {@code REQUIRES_NEW} propagation is honoured via the Spring AOP proxy - a
 * same-class self-invocation would silently bypass the transactional advice.
 */
@Service
public class IdempotentRequestCreator {

    private final PricingExceptionRequestRepository requestRepository;
    private final RequestHistoryEventRepository historyRepository;
    private final MortgageApplicationRepository applicationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public IdempotentRequestCreator(
            PricingExceptionRequestRepository requestRepository,
            RequestHistoryEventRepository historyRepository,
            MortgageApplicationRepository applicationRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            ObjectMapper objectMapper) {
        this.requestRepository = requestRepository;
        this.historyRepository = historyRepository;
        this.applicationRepository = applicationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PricingExceptionRequestService.IdempotentResult create(
            String idempotencyKey, String hash, CreateRequestDto dto, AppUser currentUser) {
        MortgageApplication application = applicationRepository.findById(dto.applicationId())
                .orElseThrow(() -> new NotFoundException("Mortgage application not found: " + dto.applicationId()));

        // Application-level pre-check for the common case: fail fast with a clear message rather
        // than relying solely on the DB constraint. Policy: at most one open (PENDING) request per
        // application at a time; once terminal, a new request may be submitted (see README for the
        // documented "supersession" policy on approved-discount).
        if (requestRepository.existsByApplicationIdAndStatus(application.getId(), RequestStatus.PENDING)) {
            throw new ConflictException(
                    "An open pricing exception request already exists for application " + application.getId()
                            + "; withdraw or resolve it before creating a new one");
        }

        PricingExceptionRequest request = new PricingExceptionRequest(
                application.getId(), dto.requestedDiscountBps(), dto.reason(), currentUser.getId());
        try {
            requestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException raceLost) {
            // Two truly concurrent creates for the same application both passed the pre-check
            // above; the unique index on pending_application_id lets only one insert win.
            throw new ConflictException(
                    "An open pricing exception request already exists for application " + application.getId()
                            + "; withdraw or resolve it before creating a new one");
        }
        historyRepository.save(new RequestHistoryEvent(request.getId(), EventType.CREATED, currentUser.getId(), null));

        RequestResponseDto responseDto = RequestResponseDto.from(request);
        String responseJson = writeJson(responseDto);

        IdempotencyRecord record = new IdempotencyRecord(idempotencyKey, currentUser.getId(), hash, 201, responseJson);
        idempotencyRecordRepository.saveAndFlush(record);

        return new PricingExceptionRequestService.IdempotentResult(201, responseJson);
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize response", e);
        }
    }
}
