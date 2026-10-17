package com.ledgerflow.ledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class TransferResponse {
    private UUID idempotencyKey;
    private Long debitTransactionId;
    private Long creditTransactionId;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private BigDecimal fromBalanceAfter;
    private BigDecimal toBalanceAfter;
    private boolean alreadyProcessed;

    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Long getDebitTransactionId() { return debitTransactionId; }
    public void setDebitTransactionId(Long debitTransactionId) { this.debitTransactionId = debitTransactionId; }

    public Long getCreditTransactionId() { return creditTransactionId; }
    public void setCreditTransactionId(Long creditTransactionId) { this.creditTransactionId = creditTransactionId; }

    public Long getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(Long fromAccountId) { this.fromAccountId = fromAccountId; }

    public Long getToAccountId() { return toAccountId; }
    public void setToAccountId(Long toAccountId) { this.toAccountId = toAccountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getFromBalanceAfter() { return fromBalanceAfter; }
    public void setFromBalanceAfter(BigDecimal fromBalanceAfter) { this.fromBalanceAfter = fromBalanceAfter; }

    public BigDecimal getToBalanceAfter() { return toBalanceAfter; }
    public void setToBalanceAfter(BigDecimal toBalanceAfter) { this.toBalanceAfter = toBalanceAfter; }

    public boolean isAlreadyProcessed() { return alreadyProcessed; }
    public void setAlreadyProcessed(boolean alreadyProcessed) { this.alreadyProcessed = alreadyProcessed; }
}
