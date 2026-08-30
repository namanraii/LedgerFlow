package com.ledgerflow.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class TransactionResponse {
    private Long transactionId;
    private UUID idempotencyKey;
    private Long accountId;
    private BigDecimal amount;
    private String transactionType;
    private String status;
    private BigDecimal riskScore;
    private String fraudCheckStatus;
    private BigDecimal balanceAfter;
    private boolean alreadyProcessed;
    private LocalDateTime createdAt;

    public TransactionResponse() {}

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public String getFraudCheckStatus() { return fraudCheckStatus; }
    public void setFraudCheckStatus(String fraudCheckStatus) { this.fraudCheckStatus = fraudCheckStatus; }

    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }

    public boolean isAlreadyProcessed() { return alreadyProcessed; }
    public void setAlreadyProcessed(boolean alreadyProcessed) { this.alreadyProcessed = alreadyProcessed; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
