package com.ledgerflow.ledger.controller;

import com.ledgerflow.ledger.domain.BalanceHistory;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.dto.AccountResponse;
import com.ledgerflow.ledger.dto.CreateAccountRequest;
import com.ledgerflow.ledger.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest req) {
        AccountResponse resp = accountService.createAccount(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable("id") Long id) {
        return ResponseEntity.ok(accountService.getAccount(id));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<Page<Transaction>> getAccountTransactions(
            @PathVariable("id") Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(accountService.getAccountTransactions(id, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<BalanceHistory>> getAccountHistory(@PathVariable("id") Long id) {
        return ResponseEntity.ok(accountService.getAccountBalanceHistory(id));
    }
}
