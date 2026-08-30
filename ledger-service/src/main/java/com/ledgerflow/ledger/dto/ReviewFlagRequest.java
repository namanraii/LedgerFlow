package com.ledgerflow.ledger.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class ReviewFlagRequest {
    @NotNull(message = "reviewer_action is required")
    @Pattern(regexp = "^(APPROVED|BLOCKED)$", message = "reviewer_action must be APPROVED or BLOCKED")
    private String reviewerAction;

    public ReviewFlagRequest() {}
    public ReviewFlagRequest(String reviewerAction) { this.reviewerAction = reviewerAction; }

    public String getReviewerAction() { return reviewerAction; }
    public void setReviewerAction(String reviewerAction) { this.reviewerAction = reviewerAction; }
}
