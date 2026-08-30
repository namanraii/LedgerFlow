package com.ledgerflow.ledger.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class FraudFlagRequest {
    @NotNull
    private Long transactionId;
    @NotNull
    private String reason;
    @NotNull
    private BigDecimal riskScore;

    public FraudFlagRequest() {}
    public FraudFlagRequest(Long transactionId, String reason, BigDecimal riskScore) {
        this.transactionId = transactionId;
        this.reason = reason;
        this.riskScore = riskScore;
    }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
}
