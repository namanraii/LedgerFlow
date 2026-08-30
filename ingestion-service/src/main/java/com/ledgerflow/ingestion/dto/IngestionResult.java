package com.ledgerflow.ingestion.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class IngestionResult {
    private UUID idempotencyKey;
    private Long transactionId;
    private Long accountId;
    private BigDecimal amount;
    private String status; // 'COMPLETED' | 'REJECTED' | 'FLAGGED' | 'FAILED_RETRYABLE'
    private BigDecimal riskScore;
    private String fraudCheckStatus; // 'OK' | 'UNAVAILABLE'
    private long latencyMs;
    private int retryAttempts;
    private String errorMessage;

    public IngestionResult() {}

    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public String getFraudCheckStatus() { return fraudCheckStatus; }
    public void setFraudCheckStatus(String fraudCheckStatus) { this.fraudCheckStatus = fraudCheckStatus; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public int getRetryAttempts() { return retryAttempts; }
    public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
