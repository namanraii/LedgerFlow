package com.ledgerflow.ledger.service;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.BalanceHistory;
import com.ledgerflow.ledger.domain.FraudFlag;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.dto.TransactionRequest;
import com.ledgerflow.ledger.dto.TransactionResponse;
import com.ledgerflow.ledger.exception.AccountNotFoundException;
import com.ledgerflow.ledger.exception.InsufficientFundsException;
import com.ledgerflow.ledger.repository.AccountRepository;
import com.ledgerflow.ledger.repository.BalanceHistoryRepository;
import com.ledgerflow.ledger.repository.FraudFlagRepository;
import com.ledgerflow.ledger.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class SafeLedgerService {
    private static final Logger log = LoggerFactory.getLogger(SafeLedgerService.class);
    private static final BigDecimal FRAUD_THRESHOLD = new BigDecimal("0.70");

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;
    private final FraudFlagRepository fraudFlagRepository;

    public SafeLedgerService(AccountRepository accountRepository,
                             TransactionRepository transactionRepository,
                             BalanceHistoryRepository balanceHistoryRepository,
                             FraudFlagRepository fraudFlagRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.balanceHistoryRepository = balanceHistoryRepository;
        this.fraudFlagRepository = fraudFlagRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = {InsufficientFundsException.class})
    public TransactionResponse processTransaction(TransactionRequest req) {
        // 1. Idempotency Check: if idempotency_key exists, return original transaction immediately
        Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(req.getIdempotencyKey());
        if (existingTx.isPresent()) {
            Transaction tx = existingTx.get();
            log.info("Idempotency hit for key={}: returning existing transaction_id={}",
                    req.getIdempotencyKey(), tx.getTransactionId());
            Account acc = accountRepository.findById(tx.getAccountId()).orElse(null);
            TransactionResponse resp = mapToResponse(tx, acc != null ? acc.getBalance() : BigDecimal.ZERO);
            resp.setAlreadyProcessed(true);
            return resp;
        }

        // 2. Acquire Pessimistic Row-Level Lock: SELECT ... FOR UPDATE
        Account account = accountRepository.findByIdWithLock(req.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(req.getAccountId()));

        BigDecimal amount = req.getAmount();
        BigDecimal balanceBefore = account.getBalance();
        boolean isDebit = "DEBIT".equalsIgnoreCase(req.getTransactionType());

        // 3. Balance Validation inside locked transaction
        if (isDebit && balanceBefore.compareTo(amount) < 0) {
            // Insufficient funds: Record REJECTED transaction for auditability
            Transaction rejectedTx = new Transaction(
                    req.getIdempotencyKey(),
                    req.getAccountId(),
                    amount,
                    req.getTransactionType(),
                    "REJECTED",
                    req.getRiskScore(),
                    req.getFraudCheckStatus()
            );
            Transaction savedRejected = transactionRepository.save(rejectedTx);
            log.warn("Rejected debit for account_id={}: balance={}, requested={}",
                    req.getAccountId(), balanceBefore, amount);
            throw new InsufficientFundsException(req.getAccountId(), balanceBefore, amount, savedRejected.getTransactionId());
        }

        // 4. Compute balance mutation
        BigDecimal balanceAfter = isDebit ? balanceBefore.subtract(amount) : balanceBefore.add(amount);
        account.setBalance(balanceAfter);
        accountRepository.save(account);

        // 5. Determine Transaction Status based on Risk Score
        String status = "COMPLETED";
        if (req.getRiskScore() != null && req.getRiskScore().compareTo(FRAUD_THRESHOLD) >= 0) {
            status = "FLAGGED";
        }

        // 6. Record Transaction
        Transaction tx = new Transaction(
                req.getIdempotencyKey(),
                req.getAccountId(),
                amount,
                req.getTransactionType(),
                status,
                req.getRiskScore(),
                req.getFraudCheckStatus()
        );
        Transaction savedTx;
        try {
            savedTx = transactionRepository.save(tx);
        } catch (DataIntegrityViolationException dive) {
            // Double safety if concurrent insert with duplicate key happened
            return transactionRepository.findByIdempotencyKey(req.getIdempotencyKey())
                    .map(existing -> {
                        TransactionResponse r = mapToResponse(existing, account.getBalance());
                        r.setAlreadyProcessed(true);
                        return r;
                    })
                    .orElseThrow(() -> dive);
        }

        // 7. Record Balance History
        BalanceHistory history = new BalanceHistory(
                account.getAccountId(),
                savedTx.getTransactionId(),
                balanceBefore,
                balanceAfter
        );
        balanceHistoryRepository.save(history);

        // 8. If FLAGGED, create durable FraudFlag
        if ("FLAGGED".equals(status)) {
            String reason = String.format("Risk score %.2f exceeded threshold 0.70", req.getRiskScore().doubleValue());
            FraudFlag flag = new FraudFlag(savedTx.getTransactionId(), reason, req.getRiskScore());
            fraudFlagRepository.save(flag);
            log.info("Durable fraud flag created for transaction_id={} score={}", savedTx.getTransactionId(), req.getRiskScore());
        }

        log.info("Safe transaction committed: id={} account={} amount={} type={} status={} balanceBefore={} balanceAfter={}",
                savedTx.getTransactionId(), req.getAccountId(), amount, req.getTransactionType(), status, balanceBefore, balanceAfter);

        return mapToResponse(savedTx, balanceAfter);
    }

    private TransactionResponse mapToResponse(Transaction tx, BigDecimal balanceAfter) {
        TransactionResponse r = new TransactionResponse();
        r.setTransactionId(tx.getTransactionId());
        r.setIdempotencyKey(tx.getIdempotencyKey());
        r.setAccountId(tx.getAccountId());
        r.setAmount(tx.getAmount());
        r.setTransactionType(tx.getTransactionType());
        r.setStatus(tx.getStatus());
        r.setRiskScore(tx.getRiskScore());
        r.setFraudCheckStatus(tx.getFraudCheckStatus());
        r.setBalanceAfter(balanceAfter);
        r.setAlreadyProcessed(false);
        r.setCreatedAt(tx.getCreatedAt());
        return r;
    }
}
