package com.ledgerflow.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AccountResponse {
    private Long accountId;
    private String accountHolder;
    private BigDecimal balance;
    private LocalDateTime createdAt;

    public AccountResponse() {}
    public AccountResponse(Long accountId, String accountHolder, BigDecimal balance, LocalDateTime createdAt) {
        this.accountId = accountId;
        this.accountHolder = accountHolder;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getAccountHolder() { return accountHolder; }
    public void setAccountHolder(String accountHolder) { this.accountHolder = accountHolder; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
