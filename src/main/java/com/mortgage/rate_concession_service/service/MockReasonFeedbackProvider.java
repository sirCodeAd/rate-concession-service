package com.mortgage.rate_concession_service.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A deterministic, rule-based stand-in for what an LLM-backed {@link ReasonFeedbackProvider}
 * would return. Chosen only to imitate the SHAPE of useful feedback (missing information, unclear claims,
 * likely reviewer questions), so the integration seam (endpoint, DTOs, config flag) can be demonstrated and tested
 * end-to-end without any model or external service. See docs/AI_REQUEST_QUALITY_ASSISTANT.md for the real-model
 * design this stands in for.
 */
@Component
@ConditionalOnProperty(name = "ai.reason-feedback.enabled", havingValue = "true", matchIfMissing = true)
public class MockReasonFeedbackProvider implements ReasonFeedbackProvider {

    static final int SHORT_REASON_THRESHOLD_CHARS = 20;

    /** At or above this discount, the mock asks for extra justification regardless of content. */
    static final int HIGH_DISCOUNT_THRESHOLD_BPS = 100;

    @Override
    public List<FeedbackItem> feedback(ReasonDraft draft) {
        List<FeedbackItem> items = new ArrayList<>();
        String reason = draft.reason() == null ? "" : draft.reason().trim();
        String lower = reason.toLowerCase(Locale.ROOT);

        if (reason.length() < SHORT_REASON_THRESHOLD_CHARS) {
            items.add(new FeedbackItem(FeedbackType.CLARITY,
                    "Reason is very brief; explain why this case needs an exception."));
        }

        boolean mentionsCompetingOffer = lower.contains("competing offer") || lower.contains("competitor");
        boolean mentionsRate = lower.contains("%") || lower.contains("bps") || lower.contains("rate");
        if (mentionsCompetingOffer && !mentionsRate) {
            items.add(new FeedbackItem(FeedbackType.MISSING_INFO,
                    "Include the competing offer's rate and lender."));
        }

        boolean mentionsTenure = lower.contains("tenure") || lower.contains("years with")
                || lower.contains("long-standing") || lower.contains("customer since")
                || lower.contains("loyal customer");
        if (!mentionsTenure) {
            items.add(new FeedbackItem(FeedbackType.MISSING_INFO,
                    "Consider stating how long the customer has been with the bank."));
        }

        if (draft.requestedDiscountBps() >= HIGH_DISCOUNT_THRESHOLD_BPS) {
            items.add(new FeedbackItem(FeedbackType.REVIEWER_QUESTION,
                    "This discount is high; explain what makes the case exceptional."));
        }

        return items;
    }
}
