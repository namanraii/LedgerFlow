package com.ledgerflow.ledger.service;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.BalanceHistory;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.dto.AccountResponse;
import com.ledgerflow.ledger.dto.CreateAccountRequest;
import com.ledgerflow.ledger.exception.AccountNotFoundException;
import com.ledgerflow.ledger.repository.AccountRepository;
import com.ledgerflow.ledger.repository.BalanceHistoryRepository;
import com.ledgerflow.ledger.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          BalanceHistoryRepository balanceHistoryRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.balanceHistoryRepository = balanceHistoryRepository;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest req) {
        Account account = new Account(req.getAccountHolder(), req.getInitialBalance());
        Account saved = accountRepository.save(account);
        log.info("Created account id={} holder='{}' initial_balance={}",
                saved.getAccountId(), saved.getAccountHolder(), saved.getBalance());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return mapToResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAllByOrderByAccountIdAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getAccountTransactions(Long accountId, Pageable pageable) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    @Transactional(readOnly = true)
    public List<BalanceHistory> getAccountBalanceHistory(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return balanceHistoryRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    private AccountResponse mapToResponse(Account a) {
        return new AccountResponse(a.getAccountId(), a.getAccountHolder(), a.getBalance(), a.getCreatedAt());
    }
}
