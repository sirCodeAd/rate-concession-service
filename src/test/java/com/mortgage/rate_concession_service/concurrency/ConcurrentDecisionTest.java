package com.mortgage.rate_concession_service.concurrency;

import com.mortgage.rate_concession_service.TestUsers;
import com.mortgage.rate_concession_service.domain.MortgageApplication;
import com.mortgage.rate_concession_service.domain.PricingExceptionRequest;
import com.mortgage.rate_concession_service.domain.RequestStatus;
import com.mortgage.rate_concession_service.repository.MortgageApplicationRepository;
import com.mortgage.rate_concession_service.repository.PricingExceptionRequestRepository;
import com.mortgage.rate_concession_service.repository.RequestHistoryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Verifies that when two reviewers race to decide on the same PENDING request at (near) the same
 * instant, exactly one decision wins (HTTP 200) and the other is rejected as a conflict (HTTP
 * 409), thanks to the conditional atomic update in
 * {@link PricingExceptionRequestRepository#transitionIfPending}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentDecisionTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PricingExceptionRequestRepository requestRepository;
    @Autowired
    private RequestHistoryEventRepository historyRepository;
    @Autowired
    private MortgageApplicationRepository applicationRepository;

    private UUID pendingRequestId;

    @BeforeEach
    void createPendingRequest() {
        // Dedicated application per test run so this doesn't collide with the seeded PENDING
        // example, or with itself across repeated runs, under the "one open request per
        // application" constraint.
        String applicationId = "app-test-" + UUID.randomUUID();
        applicationRepository.save(new MortgageApplication(applicationId, "Concurrency Test Applicant", 600));

        PricingExceptionRequest request = new PricingExceptionRequest(
                applicationId, 45, "Concurrency race test request", TestUsers.RM_1);
        requestRepository.saveAndFlush(request);
        pendingRequestId = request.getId();
    }

    @Test
    void onlyOneOfTwoConcurrentDecisions_succeeds() throws Exception {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            List<Future<Integer>> futures = List.of(
                    executor.submit(() -> decide(TestUsers.REVIEWER_1, "REVIEWER", "APPROVE", null, readyLatch, startLatch)),
                    executor.submit(() -> decide(TestUsers.REVIEWER_2, "REVIEWER", "DECLINE", "Too risky", readyLatch, startLatch))
            );

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            int status1 = futures.get(0).get(10, TimeUnit.SECONDS);
            int status2 = futures.get(1).get(10, TimeUnit.SECONDS);

            long successCount = List.of(status1, status2).stream().filter(s -> s == 200).count();
            long conflictCount = List.of(status1, status2).stream().filter(s -> s == 409).count();

            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);

            PricingExceptionRequest finalState = requestRepository.findById(pendingRequestId).orElseThrow();
            assertThat(finalState.getStatus()).isIn(RequestStatus.APPROVED, RequestStatus.DECLINED);

            assertThat(historyRepository.findByRequestIdOrderByTimestampAsc(pendingRequestId)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private int decide(
            String userId, String role, String decision, String reason, CountDownLatch readyLatch,
            CountDownLatch startLatch) throws Exception {
        readyLatch.countDown();
        startLatch.await();
        String body = reason == null
                ? "{\"decision\":\"" + decision + "\"}"
                : "{\"decision\":\"" + decision + "\",\"reason\":\"" + reason + "\"}";
        return mockMvc.perform(post("/api/requests/" + pendingRequestId + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", userId)
                        .header("X-User-Role", role)
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
