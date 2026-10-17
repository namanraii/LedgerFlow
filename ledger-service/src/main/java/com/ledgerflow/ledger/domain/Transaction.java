package com.ledgerflow.ledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_account_id", columnList = "account_id"),
    @Index(name = "idx_transactions_created_at", columnList = "created_at"),
    @Index(name = "idx_transactions_status", columnList = "status")
})
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "counterparty_account_id")
    private Long counterpartyAccountId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType; // 'DEBIT' | 'CREDIT'

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING"; // 'PENDING' | 'COMPLETED' | 'REJECTED' | 'FLAGGED' | 'FAILED_RETRYABLE'

    @Column(name = "risk_score", precision = 5, scale = 4)
    private BigDecimal riskScore;

    @Column(name = "fraud_check_status", length = 20)
    private String fraudCheckStatus = "OK"; // 'OK' | 'UNAVAILABLE'

    @Column(name = "memo", length = 160)
    private String memo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Transaction() {}

    public Transaction(UUID idempotencyKey, Long accountId, BigDecimal amount, String transactionType, String status, BigDecimal riskScore, String fraudCheckStatus) {
        this.idempotencyKey = idempotencyKey;
        this.accountId = accountId;
        this.amount = amount;
        this.transactionType = transactionType;
        this.status = status;
        this.riskScore = riskScore;
        this.fraudCheckStatus = fraudCheckStatus != null ? fraudCheckStatus : "OK";
        this.createdAt = LocalDateTime.now();
    }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Long getCounterpartyAccountId() { return counterpartyAccountId; }
    public void setCounterpartyAccountId(Long counterpartyAccountId) { this.counterpartyAccountId = counterpartyAccountId; }

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

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
