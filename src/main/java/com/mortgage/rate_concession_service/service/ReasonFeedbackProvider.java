package com.mortgage.rate_concession_service.service;

import java.util.List;

/**
 * Produces advisory feedback on a draft pricing-exception request's {@code reason} text before
 * it is ever submitted. Implementations are read-only and advisory only.
 */
public interface ReasonFeedbackProvider {

    List<FeedbackItem> feedback(ReasonDraft draft);

    record ReasonDraft(String applicationId, int requestedDiscountBps, String reason) {
    }

    record FeedbackItem(FeedbackType type, String message) {
    }

    enum FeedbackType {
        MISSING_INFO,
        CLARITY,
        REVIEWER_QUESTION
    }
}
