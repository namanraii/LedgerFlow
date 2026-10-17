package com.ledgerflow.ledger.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public class TransferRequest {
    @NotNull(message = "idempotencyKey is required")
    @JsonProperty("idempotencyKey")
    @JsonAlias({"idempotency_key"})
    private UUID idempotencyKey;

    @NotNull(message = "fromAccountId is required")
    @JsonProperty("fromAccountId")
    @JsonAlias({"from_account_id"})
    private Long fromAccountId;

    @NotNull(message = "toAccountId is required")
    @JsonProperty("toAccountId")
    @JsonAlias({"to_account_id"})
    private Long toAccountId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be strictly positive")
    private BigDecimal amount;

    @Size(max = 160, message = "memo must be 160 characters or fewer")
    private String memo;

    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Long getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(Long fromAccountId) { this.fromAccountId = fromAccountId; }

    public Long getToAccountId() { return toAccountId; }
    public void setToAccountId(Long toAccountId) { this.toAccountId = toAccountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
}
