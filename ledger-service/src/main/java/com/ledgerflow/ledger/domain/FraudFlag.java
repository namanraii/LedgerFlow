package com.ledgerflow.ledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_flags")
public class FraudFlag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "risk_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal riskScore;

    @Column(name = "reviewed", nullable = false)
    private Boolean reviewed = false;

    @Column(name = "reviewer_action", length = 20)
    private String reviewerAction; // 'APPROVED' | 'BLOCKED' | null

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public FraudFlag() {}

    public FraudFlag(Long transactionId, String reason, BigDecimal riskScore) {
        this.transactionId = transactionId;
        this.reason = reason;
        this.riskScore = riskScore;
        this.reviewed = false;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public Boolean getReviewed() { return reviewed; }
    public void setReviewed(Boolean reviewed) { this.reviewed = reviewed; }

    public String getReviewerAction() { return reviewerAction; }
    public void setReviewerAction(String reviewerAction) { this.reviewerAction = reviewerAction; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
