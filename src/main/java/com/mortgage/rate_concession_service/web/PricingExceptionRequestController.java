package com.mortgage.rate_concession_service.web;

import com.mortgage.rate_concession_service.domain.AppUser;
import com.mortgage.rate_concession_service.domain.PricingExceptionRequest;
import com.mortgage.rate_concession_service.domain.RequestHistoryEvent;
import com.mortgage.rate_concession_service.domain.RequestStatus;
import com.mortgage.rate_concession_service.domain.UserRole;
import com.mortgage.rate_concession_service.exception.BadRequestException;
import com.mortgage.rate_concession_service.exception.ForbiddenException;
import com.mortgage.rate_concession_service.security.CurrentUser;
import com.mortgage.rate_concession_service.service.PricingExceptionRequestService;
import com.mortgage.rate_concession_service.web.dto.CreateRequestDto;
import com.mortgage.rate_concession_service.web.dto.DecisionRequestDto;
import com.mortgage.rate_concession_service.web.dto.HistoryEventDto;
import com.mortgage.rate_concession_service.web.dto.RequestResponseDto;
import com.mortgage.rate_concession_service.web.dto.WithdrawRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/requests")
public class PricingExceptionRequestController {

    /** Matches the {@code idempotency_key varchar(255)} column in V1__init_schema.sql. */
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    private final PricingExceptionRequestService service;

    public PricingExceptionRequestController(PricingExceptionRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<String> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateRequestDto dto,
            @CurrentUser AppUser currentUser) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("The Idempotency-Key header is required for POST /api/requests");
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BadRequestException(
                    "The Idempotency-Key header must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        requireRole(currentUser, UserRole.RELATIONSHIP_MANAGER, "create a pricing exception request");

        PricingExceptionRequestService.IdempotentResult result = service.createRequest(idempotencyKey, dto, currentUser);
        return ResponseEntity.status(result.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.responseBodyJson());
    }

    @GetMapping("/{id}")
    public RequestResponseDto get(@PathVariable UUID id, @CurrentUser AppUser currentUser) {
        return RequestResponseDto.from(service.getOrThrow(id));
    }

    @GetMapping("/{id}/history")
    public List<HistoryEventDto> history(@PathVariable UUID id, @CurrentUser AppUser currentUser) {
        List<RequestHistoryEvent> events = service.getHistory(id);
        return events.stream().map(HistoryEventDto::from).toList();
    }

    /**
     * Lists/filters requests, newest-created first. Not paginated - see README §11 for why
     * (this is a documented, acceptable exercise-scope trade-off, not an oversight) - but the
     * ordering is deterministic so API/UI behaviour is predictable even without paging.
     */
    @GetMapping
    public List<RequestResponseDto> list(
            @RequestParam(required = false) String applicationId,
            @RequestParam(required = false) RequestStatus status,
            @CurrentUser AppUser currentUser) {
        return service.list(applicationId, status).stream().map(RequestResponseDto::from).toList();
    }

    @PostMapping("/{id}/decision")
    public RequestResponseDto decide(
            @PathVariable UUID id,
            @Valid @RequestBody DecisionRequestDto dto,
            @CurrentUser AppUser currentUser) {
        requireRole(currentUser, UserRole.REVIEWER, "decide on a pricing exception request");
        if (dto.decision() == DecisionRequestDto.Decision.DECLINE
                && (dto.reason() == null || dto.reason().isBlank())) {
            throw new BadRequestException("reason is required when declining a request");
        }
        PricingExceptionRequest updated = service.decide(id, dto, currentUser);
        return RequestResponseDto.from(updated);
    }

    @PostMapping("/{id}/withdraw")
    public RequestResponseDto withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) WithdrawRequestDto dto,
            @CurrentUser AppUser currentUser) {
        requireRole(currentUser, UserRole.RELATIONSHIP_MANAGER, "withdraw a pricing exception request");
        String reason = dto == null ? null : dto.reason();
        PricingExceptionRequest updated = service.withdraw(id, reason, currentUser);
        return RequestResponseDto.from(updated);
    }

    private void requireRole(AppUser user, UserRole required, String action) {
        if (user.getRole() != required) {
            throw new ForbiddenException("Only a " + required + " may " + action);
        }
    }
}
