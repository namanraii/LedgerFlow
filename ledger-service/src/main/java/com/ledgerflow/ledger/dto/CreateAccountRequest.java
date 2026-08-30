package com.ledgerflow.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public class CreateAccountRequest {
    @NotBlank(message = "Account holder name is required")
    private String accountHolder;

    @NotNull(message = "Initial balance is required")
    @PositiveOrZero(message = "Initial balance cannot be negative")
    private BigDecimal initialBalance;

    public CreateAccountRequest() {}
    public CreateAccountRequest(String accountHolder, BigDecimal initialBalance) {
        this.accountHolder = accountHolder;
        this.initialBalance = initialBalance;
    }

    public String getAccountHolder() { return accountHolder; }
    public void setAccountHolder(String accountHolder) { this.accountHolder = accountHolder; }

    public BigDecimal getInitialBalance() { return initialBalance; }
    public void setInitialBalance(BigDecimal initialBalance) { this.initialBalance = initialBalance; }
}
