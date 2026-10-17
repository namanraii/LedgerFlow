package com.ledgerflow.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public class IngestionRequest {
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
    @Pattern(regexp = "DEBIT|CREDIT", message = "transactionType must be DEBIT or CREDIT")
    @JsonProperty("transactionType")
    @JsonAlias({"transaction_type"})
    private String transactionType;

    @JsonProperty("safe")
    private boolean safe = true;

    public IngestionRequest() {
        this.idempotencyKey = UUID.randomUUID();
    }

    public IngestionRequest(Long accountId, BigDecimal amount, String transactionType) {
        this.idempotencyKey = UUID.randomUUID();
        this.accountId = accountId;
        this.amount = amount;
        this.transactionType = transactionType;
        this.safe = true;
    }

    public UUID getIdempotencyKey() { 
        if (this.idempotencyKey == null) {
            this.idempotencyKey = UUID.randomUUID();
        }
        return idempotencyKey; 
    }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public boolean isSafe() { return safe; }
    public void setSafe(boolean safe) { this.safe = safe; }
}
