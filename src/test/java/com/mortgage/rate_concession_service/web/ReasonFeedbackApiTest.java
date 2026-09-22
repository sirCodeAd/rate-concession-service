package com.mortgage.rate_concession_service.web;

import com.mortgage.rate_concession_service.TestUsers;
import com.mortgage.rate_concession_service.repository.PricingExceptionRequestRepository;
import com.mortgage.rate_concession_service.repository.RequestHistoryEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Coverage for {@code POST /api/requests/reason-feedback} (the "Request Quality Assistant" mock -
 * see docs/AI_REQUEST_QUALITY_ASSISTANT.md). The disabled-flag case lives in
 * {@link ReasonFeedbackDisabledApiTest} since it needs a different Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReasonFeedbackApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PricingExceptionRequestRepository requestRepository;
    @Autowired
    private RequestHistoryEventRepository historyRepository;

    @Test
    void relationshipManager_getsFeedbackOnVagueDraft() throws Exception {
        mockMvc.perform(post("/api/requests/reason-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .content("""
                                {"applicationId":"app-1001","requestedDiscountBps":25,"reason":"good customer"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.promptVersion").value("mock-v1"))
                .andExpect(jsonPath("$.feedback").isArray())
                .andExpect(jsonPath("$.feedback[0].type").exists())
                .andExpect(jsonPath("$.feedback[0].message").exists());
    }

    @Test
    void wellExplainedDraft_getsNoFeedback() throws Exception {
        mockMvc.perform(post("/api/requests/reason-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .content("""
                                {"applicationId":"app-1001","requestedDiscountBps":30,
                                 "reason":"Customer tenure of 8 years with strong repayment history and a competing offer of 3.1%% from another bank."}
                                """.replace("%%", "%")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").isEmpty());
    }

    @Test
    void blankReason_isValidationError() throws Exception {
        mockMvc.perform(post("/api/requests/reason-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .content("""
                                {"applicationId":"app-1001","requestedDiscountBps":25,"reason":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviewer_isForbidden() throws Exception {
        mockMvc.perform(post("/api/requests/reason-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.REVIEWER_1)
                        .header("X-User-Role", "REVIEWER")
                        .content("""
                                {"applicationId":"app-1001","requestedDiscountBps":25,"reason":"good customer"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void doesNotWriteAnyRequestOrHistoryEvent() throws Exception {
        long requestsBefore = requestRepository.count();
        long historyBefore = historyRepository.count();

        mockMvc.perform(post("/api/requests/reason-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .content("""
                                {"applicationId":"app-1001","requestedDiscountBps":150,"reason":"good customer, please approve"}
                                """))
                .andExpect(status().isOk());

        assertThat(requestRepository.count()).isEqualTo(requestsBefore);
        assertThat(historyRepository.count()).isEqualTo(historyBefore);
    }
}
