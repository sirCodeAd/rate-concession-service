package com.mortgage.rate_concession_service.web.dto;

import com.mortgage.rate_concession_service.service.ReasonFeedbackProvider.FeedbackItem;

import java.util.List;

/**
 * Response body for {@code POST /api/requests/reason-feedback}. {@code provider} and
 * {@code promptVersion} are always present so a client can tell which implementation produced
 * the feedback (today: always the mock).
 */
public record ReasonFeedbackResponseDto(List<FeedbackItem> feedback, String provider, String promptVersion) {
}
