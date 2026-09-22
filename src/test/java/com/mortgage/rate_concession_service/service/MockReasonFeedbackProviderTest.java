package com.mortgage.rate_concession_service.service;

import com.mortgage.rate_concession_service.service.ReasonFeedbackProvider.FeedbackItem;
import com.mortgage.rate_concession_service.service.ReasonFeedbackProvider.FeedbackType;
import com.mortgage.rate_concession_service.service.ReasonFeedbackProvider.ReasonDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the mock's deterministic heuristics - see class Javadoc for what each imitates. */
class MockReasonFeedbackProviderTest {

    private final MockReasonFeedbackProvider provider = new MockReasonFeedbackProvider();

    @Test
    void veryShortReason_flagsClarity() {
        List<FeedbackItem> feedback = provider.feedback(new ReasonDraft("app-1", 25, "good customer"));
        assertThat(feedback).extracting(FeedbackItem::type).contains(FeedbackType.CLARITY);
    }

    @Test
    void longEnoughReason_doesNotFlagClarity() {
        List<FeedbackItem> feedback = provider.feedback(new ReasonDraft(
                "app-1", 25, "Customer has been with the bank for over ten years and always pays on time."));
        assertThat(feedback).extracting(FeedbackItem::type).doesNotContain(FeedbackType.CLARITY);
    }

    @Test
    void competingOfferWithoutRate_flagsMissingInfo() {
        List<FeedbackItem> feedback = provider.feedback(new ReasonDraft(
                "app-1", 25, "Customer mentioned they have a competing offer from another lender and tenure of 8 years."));
        assertThat(feedback)
                .anyMatch(f -> f.type() == FeedbackType.MISSING_INFO
                        && f.message().contains("competing offer's rate"));
    }

    @Test
    void competingOfferWithRate_doesNotFlagThatMissingInfo() {
        List<FeedbackItem> feedback = provider.feedback(new ReasonDraft(
                "app-1", 25,
                "Customer has a competing offer of 3.2% from another bank and has 8 years of tenure with us."));
        assertThat(feedback).noneMatch(f -> f.message().contains("competing offer's rate"));
    }

    @Test
    void noTenureMentioned_flagsMissingInfo() {
        List<FeedbackItem> feedback = provider.feedback(new ReasonDraft(
                "app-1", 25, "Customer requested a lower rate because they found a cheaper option elsewhere."));
        assertThat(feedback)
                .anyMatch(f -> f.type() == FeedbackType.MISSING_INFO
                        && f.message().contains("how long the customer has been with the bank"));
    }

    @Test
    void tenureMentioned_doesNotFlagThatMissingInfo() {
        List<FeedbackItem> feedback = provider.feedback(new ReasonDraft(
                "app-1", 25, "Customer tenure of 8 years and consistently strong repayment history."));
        assertThat(feedback).noneMatch(f -> f.message().contains("how long the customer has been"));
    }

    @Test
    void highDiscount_flagsReviewerQuestion() {
        List<FeedbackItem> feedback = provider.feedback(new ReasonDraft(
                "app-1", 100, "Customer tenure of 8 years and a competing offer of 3.1% from another bank."));
        assertThat(feedback).extracting(FeedbackItem::type).contains(FeedbackType.REVIEWER_QUESTION);
    }

    @Test
    void lowDiscount_doesNotFlagReviewerQuestion() {
        List<FeedbackItem> feedback = provider.feedback(new ReasonDraft(
                "app-1", 99, "Customer tenure of 8 years and a competing offer of 3.1% from another bank."));
        assertThat(feedback).extracting(FeedbackItem::type).doesNotContain(FeedbackType.REVIEWER_QUESTION);
    }

    @Test
    void wellExplainedRequest_producesNoFeedback() {
        List<FeedbackItem> feedback = provider.feedback(new ReasonDraft(
                "app-1", 30,
                "Customer tenure of 8 years with strong repayment history and a competing offer of 3.1% from another bank."));
        assertThat(feedback).isEmpty();
    }
}
