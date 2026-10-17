package com.ledgerflow.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FlaggedReportRow {
    private final String accountHolder;
    private final Long transactionId;
    private final Long accountId;
    private final BigDecimal amount;
    private final BigDecimal riskScore;
    private final String reason;
    private final Long riskRank;
    private final Long flagId;
    private final LocalDateTime createdAt;

    public FlaggedReportRow(String accountHolder, Long transactionId, Long accountId, BigDecimal amount,
                            BigDecimal riskScore, String reason, Long riskRank, Long flagId, LocalDateTime createdAt) {
        this.accountHolder = accountHolder;
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount;
        this.riskScore = riskScore;
        this.reason = reason;
        this.riskRank = riskRank;
        this.flagId = flagId;
        this.createdAt = createdAt;
    }

    public String getAccountHolder() { return accountHolder; }
    public Long getTransactionId() { return transactionId; }
    public Long getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getRiskScore() { return riskScore; }
    public String getReason() { return reason; }
    public Long getRiskRank() { return riskRank; }
    public Long getFlagId() { return flagId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
