package com.ledgerflow.ledger.service;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.BalanceHistory;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.dto.TransactionRequest;
import com.ledgerflow.ledger.dto.TransactionResponse;
import com.ledgerflow.ledger.exception.AccountNotFoundException;
import com.ledgerflow.ledger.exception.InsufficientFundsException;
import com.ledgerflow.ledger.repository.AccountRepository;
import com.ledgerflow.ledger.repository.BalanceHistoryRepository;
import com.ledgerflow.ledger.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * UNSAFE IMPLEMENTATION (For Controlled Concurrency Experiment).
 * Deliberately performs read-modify-write without row-level locking or atomic transactions.
 * Demonstrates the lost-update race condition under concurrent load.
 */
@Service
public class UnsafeLedgerService {
    private static final Logger log = LoggerFactory.getLogger(UnsafeLedgerService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;

    public UnsafeLedgerService(AccountRepository accountRepository,
                               TransactionRepository transactionRepository,
                               BalanceHistoryRepository balanceHistoryRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.balanceHistoryRepository = balanceHistoryRepository;
    }

    public TransactionResponse processTransactionUnsafe(TransactionRequest req) {
        // 1. Read balance WITHOUT row-level lock
        Account account = accountRepository.findById(req.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(req.getAccountId()));

        BigDecimal amount = req.getAmount();
        BigDecimal balanceBefore = account.getBalance();
        boolean isDebit = "DEBIT".equalsIgnoreCase(req.getTransactionType());

        if (isDebit && balanceBefore.compareTo(amount) < 0) {
            throw new InsufficientFundsException(req.getAccountId(), balanceBefore, amount, null);
        }

        // Artificial application delay to widen race-condition window
        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // 2. Compute new balance in application memory based on stale read
        BigDecimal balanceAfter = isDebit ? balanceBefore.subtract(amount) : balanceBefore.add(amount);
        account.setBalance(balanceAfter);

        // 3. Write back stale balance
        accountRepository.save(account);

        // 4. Record transaction and history
        Transaction tx = new Transaction(
                req.getIdempotencyKey(),
                req.getAccountId(),
                amount,
                req.getTransactionType(),
                "COMPLETED",
                req.getRiskScore(),
                req.getFraudCheckStatus()
        );
        Transaction savedTx = transactionRepository.save(tx);

        BalanceHistory history = new BalanceHistory(
                account.getAccountId(),
                savedTx.getTransactionId(),
                balanceBefore,
                balanceAfter
        );
        balanceHistoryRepository.save(history);

        log.warn("[UNSAFE] Overwrote balance for account_id={}: before={}, after={}",
                account.getAccountId(), balanceBefore, balanceAfter);

        TransactionResponse r = new TransactionResponse();
        r.setTransactionId(savedTx.getTransactionId());
        r.setIdempotencyKey(savedTx.getIdempotencyKey());
        r.setAccountId(savedTx.getAccountId());
        r.setAmount(savedTx.getAmount());
        r.setTransactionType(savedTx.getTransactionType());
        r.setStatus(savedTx.getStatus());
        r.setBalanceAfter(balanceAfter);
        r.setCreatedAt(savedTx.getCreatedAt());
        return r;
    }
}
