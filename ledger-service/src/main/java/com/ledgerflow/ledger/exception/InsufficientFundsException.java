package com.ledgerflow.ledger.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
    private final Long accountId;
    private final BigDecimal currentBalance;
    private final BigDecimal requestedAmount;
    private final Long transactionId;

    public InsufficientFundsException(Long accountId, BigDecimal currentBalance, BigDecimal requestedAmount, Long transactionId) {
        super(String.format("Insufficient funds for account %d. Current balance: %s, requested: %s", accountId, currentBalance, requestedAmount));
        this.accountId = accountId;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
        this.transactionId = transactionId;
    }

    public Long getAccountId() { return accountId; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public Long getTransactionId() { return transactionId; }
}
