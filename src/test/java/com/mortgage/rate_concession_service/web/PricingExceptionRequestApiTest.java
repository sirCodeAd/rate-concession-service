package com.mortgage.rate_concession_service.web;

import tools.jackson.databind.JsonNode;
import com.mortgage.rate_concession_service.TestUsers;
import com.mortgage.rate_concession_service.domain.MortgageApplication;
import com.mortgage.rate_concession_service.repository.MortgageApplicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end MockMvc coverage of the full workflow: create -> approve/decline/withdraw, plus
 * authorization, validation, idempotency and listing behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PricingExceptionRequestApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;
    @Autowired
    private MortgageApplicationRepository applicationRepository;

    private String createBody(String applicationId, int discountBps, String reason) {
        return """
                {"applicationId":"%s","requestedDiscountBps":%d,"reason":"%s"}
                """.formatted(applicationId, discountBps, reason);
    }

    /**
     * Creates a brand-new, dedicated {@link MortgageApplication} so tests that need to create a
     * genuine PENDING request don't collide with the seeded requests, or with each other, under
     * the "at most one open request per application" constraint.
     */
    private String freshApplicationId() {
        String id = "app-test-" + UUID.randomUUID();
        applicationRepository.save(new MortgageApplication(id, "Test Applicant", 600));
        return id;
    }

    private JsonNode createRequest(String applicationId, int discountBps, String reason, String idempotencyKey)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", idempotencyKey)
                        .content(createBody(applicationId, discountBps, reason)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    @Test
    void happyPath_createApproveThenApprovedDiscountAndHistory() throws Exception {
        String applicationId = freshApplicationId();
        String key = UUID.randomUUID().toString();
        JsonNode created = createRequest(applicationId, 30, "Happy path approval test", key);
        String requestId = created.get("id").asText();
        assertThat(created.get("status").asText()).isEqualTo("PENDING");

        mockMvc.perform(post("/api/requests/" + requestId + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.REVIEWER_1)
                        .header("X-User-Role", "REVIEWER")
                        .content("""
                                {"decision":"APPROVE","reason":"Looks good"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/applications/" + applicationId + "/approved-discount")
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApprovedDiscount").value(true))
                .andExpect(jsonPath("$.discountBps").value(30));

        mockMvc.perform(get("/api/requests/" + requestId + "/history")
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("CREATED"))
                .andExpect(jsonPath("$[1].eventType").value("APPROVED"));
    }

    @Test
    void declinePath() throws Exception {
        String key = UUID.randomUUID().toString();
        JsonNode created = createRequest(freshApplicationId(), 40, "Decline path test", key);
        String requestId = created.get("id").asText();

        mockMvc.perform(post("/api/requests/" + requestId + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.REVIEWER_1)
                        .header("X-User-Role", "REVIEWER")
                        .content("""
                                {"decision":"DECLINE","reason":"Discount too large"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        mockMvc.perform(get("/api/requests/" + requestId + "/history")
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(jsonPath("$[1].eventType").value("DECLINED"));
    }

    @Test
    void declineRequiresReason() throws Exception {
        String key = UUID.randomUUID().toString();
        JsonNode created = createRequest(freshApplicationId(), 40, "Decline requires reason test", key);
        String requestId = created.get("id").asText();

        mockMvc.perform(post("/api/requests/" + requestId + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.REVIEWER_1)
                        .header("X-User-Role", "REVIEWER")
                        .content("""
                                {"decision":"DECLINE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdrawPath_thenDecisionAfterIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        JsonNode created = createRequest(freshApplicationId(), 20, "Withdraw path test", key);
        String requestId = created.get("id").asText();

        mockMvc.perform(post("/api/requests/" + requestId + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .content("""
                                {"reason":"Client changed their mind"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        mockMvc.perform(post("/api/requests/" + requestId + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.REVIEWER_1)
                        .header("X-User-Role", "REVIEWER")
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void withdraw_byNonCreatingRelationshipManager_isAllowed() throws Exception {
        // Any authenticated RM may withdraw a pending request, not only its creator - this
        // supports continuity (e.g. covering for an absent colleague), mirroring how any
        // REVIEWER may decide on a request regardless of who it was assigned to.
        String key = UUID.randomUUID().toString();
        JsonNode created = createRequest(freshApplicationId(), 20, "Non creator withdraw test", key);
        String requestId = created.get("id").asText();

        mockMvc.perform(post("/api/requests/" + requestId + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_2)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void withdraw_byReviewer_isForbidden() throws Exception {
        // The role check remains: reviewers may never withdraw, regardless of ownership.
        String key = UUID.randomUUID().toString();
        JsonNode created = createRequest(freshApplicationId(), 20, "Reviewer withdraw test", key);
        String requestId = created.get("id").asText();

        mockMvc.perform(post("/api/requests/" + requestId + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.REVIEWER_1)
                        .header("X-User-Role", "REVIEWER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void withdraw_alreadyDecidedConflict() throws Exception {
        String key = UUID.randomUUID().toString();
        JsonNode created = createRequest(freshApplicationId(), 20, "Already decided withdraw test", key);
        String requestId = created.get("id").asText();

        mockMvc.perform(post("/api/requests/" + requestId + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.REVIEWER_1)
                        .header("X-User-Role", "REVIEWER")
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/requests/" + requestId + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(status().isConflict());
    }

    @Test
    void decidingAlreadyDecidedRequest_isConflict() throws Exception {
        String key = UUID.randomUUID().toString();
        JsonNode created = createRequest(freshApplicationId(), 20, "Double decide test", key);
        String requestId = created.get("id").asText();

        mockMvc.perform(post("/api/requests/" + requestId + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.REVIEWER_1)
                        .header("X-User-Role", "REVIEWER")
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/requests/" + requestId + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.REVIEWER_2)
                        .header("X-User-Role", "REVIEWER")
                        .content("""
                                {"decision":"DECLINE","reason":"Too late"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void authz_rmCannotDecide() throws Exception {
        String key = UUID.randomUUID().toString();
        JsonNode created = createRequest(freshApplicationId(), 20, "RM cannot decide test", key);
        String requestId = created.get("id").asText();

        mockMvc.perform(post("/api/requests/" + requestId + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void authz_reviewerCannotCreate() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.REVIEWER_1)
                        .header("X-User-Role", "REVIEWER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(createBody("app-1001", 20, "Reviewer cannot create test")))
                .andExpect(status().isForbidden());
    }

    @Test
    void authz_unknownUserId_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "no-such-user")
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(createBody("app-1001", 20, "Unknown user test")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authz_missingHeaders_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(createBody("app-1001", 20, "Missing headers test")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingIdempotencyKey_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .content(createBody("app-1001", 20, "Missing idempotency key test")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validation_invalidDiscount_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(createBody("app-1001", 0, "Invalid discount test")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(createBody("app-1001", 501, "Invalid discount test 2")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(createBody("app-1001", -5, "Invalid discount test 3")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validation_blankReason_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(createBody("app-1001", 20, "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validation_unknownApplicationId_isNotFound() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(createBody("app-unknown-999", 20, "Unknown app test")))
                .andExpect(status().isNotFound());
    }

    @Test
    void idempotency_sameKeySameBody_returnsSameResponseAndNoDuplicate() throws Exception {
        String applicationId = freshApplicationId();
        String key = UUID.randomUUID().toString();
        String body = createBody(applicationId, 33, "Idempotent dedupe test");

        MvcResult first = mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", key)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", key)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());

        JsonNode firstNode = objectMapper.readTree(first.getResponse().getContentAsByteArray());
        String requestId = firstNode.get("id").asText();

        mockMvc.perform(get("/api/requests")
                        .param("applicationId", applicationId)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode list = objectMapper.readTree(result.getResponse().getContentAsByteArray());
                    long matches = 0;
                    for (JsonNode node : list) {
                        if (node.get("id").asText().equals(requestId)) {
                            matches++;
                        }
                    }
                    assertThat(matches).isEqualTo(1);
                });
    }

    @Test
    void idempotency_sameKeyDifferentBody_isConflict() throws Exception {
        String applicationId = freshApplicationId();
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", key)
                        .content(createBody(applicationId, 10, "First payload for key reuse test")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", key)
                        .content(createBody(applicationId, 99, "Different payload for key reuse test")))
                .andExpect(status().isConflict());
    }

    @Test
    void idempotency_sameKeyDifferentManager_isIndependent() throws Exception {
        // Same key, same-looking body, but two different RMs: each gets their own request rather
        // than one silently reusing the other's stored response.
        String applicationIdForRm1 = freshApplicationId();
        String applicationIdForRm2 = freshApplicationId();
        String key = UUID.randomUUID().toString();

        MvcResult rm1Result = mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", key)
                        .content(createBody(applicationIdForRm1, 15, "RM1 shared-key test")))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult rm2Result = mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_2)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", key)
                        .content(createBody(applicationIdForRm2, 15, "RM2 shared-key test")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode rm1Node = objectMapper.readTree(rm1Result.getResponse().getContentAsByteArray());
        JsonNode rm2Node = objectMapper.readTree(rm2Result.getResponse().getContentAsByteArray());

        assertThat(rm1Node.get("id").asText()).isNotEqualTo(rm2Node.get("id").asText());
        assertThat(rm1Node.get("applicationId").asText()).isEqualTo(applicationIdForRm1);
        assertThat(rm2Node.get("applicationId").asText()).isEqualTo(applicationIdForRm2);
    }

    @Test
    void create_duplicatePendingForSameApplication_isConflict() throws Exception {
        String applicationId = freshApplicationId();

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(createBody(applicationId, 20, "First open request")))
                .andExpect(status().isCreated());

        // A different RM, different idempotency key, same application: still rejected while the
        // first request is open (PENDING).
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_2)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(createBody(applicationId, 45, "Second open request attempt")))
                .andExpect(status().isConflict());
    }

    @Test
    void create_afterPriorRequestIsTerminal_supersedesApprovedDiscount() throws Exception {
        String applicationId = freshApplicationId();

        JsonNode firstRequest = createRequest(applicationId, 20, "First negotiated discount", UUID.randomUUID().toString());
        mockMvc.perform(post("/api/requests/" + firstRequest.get("id").asText() + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.REVIEWER_1)
                        .header("X-User-Role", "REVIEWER")
                        .content("""
                                {"decision":"APPROVE","reason":"Initial approval"}
                                """))
                .andExpect(status().isOk());

        // First request is now terminal (APPROVED), so a second request for the same application
        // is allowed - this is the documented "supersession" policy: re-pricing over the life of
        // an application is legitimate, and the most recently approved request is authoritative.
        JsonNode secondRequest = createRequest(applicationId, 40, "Renegotiated discount", UUID.randomUUID().toString());
        mockMvc.perform(post("/api/requests/" + secondRequest.get("id").asText() + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.REVIEWER_2)
                        .header("X-User-Role", "REVIEWER")
                        .content("""
                                {"decision":"APPROVE","reason":"Renegotiation approved"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/applications/" + applicationId + "/approved-discount")
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountBps").value(40))
                .andExpect(jsonPath("$.requestId").value(secondRequest.get("id").asText()));
    }

    @Test
    void create_oversizedReason_isBadRequestNotServerError() throws Exception {
        String oversizedReason = "x".repeat(2001);
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(createBody(freshApplicationId(), 20, oversizedReason)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_oversizedApplicationId_isBadRequestNotServerError() throws Exception {
        String oversizedApplicationId = "app-" + "x".repeat(100);
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(createBody(oversizedApplicationId, 20, "Oversized application id test")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_oversizedIdempotencyKey_isBadRequestNotServerError() throws Exception {
        String oversizedKey = "k".repeat(256);
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .header("Idempotency-Key", oversizedKey)
                        .content(createBody(freshApplicationId(), 20, "Oversized idempotency key test")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void decision_oversizedReason_isBadRequestNotServerError() throws Exception {
        JsonNode created = createRequest(freshApplicationId(), 20, "Base request for oversized decision reason", UUID.randomUUID().toString());
        String requestId = created.get("id").asText();
        String oversizedReason = "x".repeat(2001);

        mockMvc.perform(post("/api/requests/" + requestId + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.REVIEWER_1)
                        .header("X-User-Role", "REVIEWER")
                        .content("""
                                {"decision":"DECLINE","reason":"%s"}
                                """.formatted(oversizedReason)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdraw_oversizedReason_isBadRequestNotServerError() throws Exception {
        JsonNode created = createRequest(freshApplicationId(), 20, "Base request for oversized withdraw reason", UUID.randomUUID().toString());
        String requestId = created.get("id").asText();
        String oversizedReason = "x".repeat(2001);

        mockMvc.perform(post("/api/requests/" + requestId + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER")
                        .content("""
                                {"reason":"%s"}
                                """.formatted(oversizedReason)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFilter_byApplicationIdAndStatus() throws Exception {
        mockMvc.perform(get("/api/requests")
                        .param("applicationId", "app-1002")
                        .param("status", "APPROVED")
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode list = objectMapper.readTree(result.getResponse().getContentAsByteArray());
                    for (JsonNode node : list) {
                        assertThat(node.get("applicationId").asText()).isEqualTo("app-1002");
                        assertThat(node.get("status").asText()).isEqualTo("APPROVED");
                    }
                });
    }

    @Test
    void list_isOrderedNewestCreatedFirst() throws Exception {
        String applicationId = freshApplicationId();

        JsonNode older = createRequest(applicationId, 10, "Older request", UUID.randomUUID().toString());
        mockMvc.perform(post("/api/requests/" + older.get("id").asText() + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(status().isOk());

        JsonNode newer = createRequest(applicationId, 20, "Newer request", UUID.randomUUID().toString());

        mockMvc.perform(get("/api/requests")
                        .param("applicationId", applicationId)
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newer.get("id").asText()))
                .andExpect(jsonPath("$[1].id").value(older.get("id").asText()));
    }

    @Test
    void getRequest_notFound() throws Exception {
        mockMvc.perform(get("/api/requests/" + UUID.randomUUID())
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approvedDiscount_noneYet_returns200WithHasApprovedDiscountFalse() throws Exception {
        mockMvc.perform(get("/api/applications/app-1003/approved-discount")
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApprovedDiscount").value(false))
                .andExpect(jsonPath("$.discountBps").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void approvedDiscount_unknownApplication_is404() throws Exception {
        mockMvc.perform(get("/api/applications/app-does-not-exist/approved-discount")
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approvedDiscount_blankApplicationIdInPath_is404NotServerError() throws Exception {
        // A blank applicationId collapses the URL to a double slash (/api/applications//approved-
        // discount), which never matches the {applicationId} route and falls through to Spring's
        // static-resource handling as a NoResourceFoundException. This must be mapped to 404, not
        // swallowed by the generic exception handler as a 500.
        mockMvc.perform(get("/api/applications//approved-discount")
                        .header("X-User-Id", TestUsers.RM_1)
                        .header("X-User-Role", "RELATIONSHIP_MANAGER"))
                .andExpect(status().isNotFound());
    }
}
