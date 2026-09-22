package com.mortgage.rate_concession_service.web;

import com.mortgage.rate_concession_service.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@code ai.reason-feedback.enabled=false} disables the endpoint entirely (no
 * {@code MockReasonFeedbackProvider} bean is created, so the controller has nothing to call).
 * Uses its own Spring context (different property = different context cache key) rather than
 * sharing {@link ReasonFeedbackApiTest}'s.
 */
@SpringBootTest(properties = "ai.reason-feedback.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReasonFeedbackDisabledApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void disabledFlag_returns404() throws Exception {
        mockMvc.perform(post("/api/requests/reason-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .content("""
                                {"applicationId":"app-1001","requestedDiscountBps":25,"reason":"good customer"}
                                """))
                .andExpect(status().isNotFound());
    }
}
