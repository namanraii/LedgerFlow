package com.ledgerflow.ledger.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public class TransactionRequest {
    @NotNull(message = "idempotencyKey is required")
    @JsonProperty("idempotencyKey")
    @JsonAlias({"idempotency_key"})
    private UUID idempotencyKey;

    @NotNull(message = "accountId is required")
    @JsonProperty("accountId")
    @JsonAlias({"account_id"})
    private Long accountId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be strictly positive")
    @JsonProperty("amount")
    private BigDecimal amount;

    @NotNull(message = "transactionType is required")
    @Pattern(regexp = "^(DEBIT|CREDIT)$", message = "transactionType must be DEBIT or CREDIT")
    @JsonProperty("transactionType")
    @JsonAlias({"transaction_type"})
    private String transactionType;

    @JsonProperty("riskScore")
    @JsonAlias({"risk_score"})
    private BigDecimal riskScore;

    @JsonProperty("fraudCheckStatus")
    @JsonAlias({"fraud_check_status"})
    private String fraudCheckStatus = "OK";

    @JsonProperty("safe")
    private boolean safe = true;

    public TransactionRequest() {}

    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public String getFraudCheckStatus() { return fraudCheckStatus; }
    public void setFraudCheckStatus(String fraudCheckStatus) { this.fraudCheckStatus = fraudCheckStatus; }

    public boolean isSafe() { return safe; }
    public void setSafe(boolean safe) { this.safe = safe; }
}
