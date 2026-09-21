package com.mortgage.rate_concession_service.repository;

import com.mortgage.rate_concession_service.domain.RequestHistoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RequestHistoryEventRepository extends JpaRepository<RequestHistoryEvent, Long> {

    List<RequestHistoryEvent> findByRequestIdOrderByTimestampAsc(UUID requestId);
}
