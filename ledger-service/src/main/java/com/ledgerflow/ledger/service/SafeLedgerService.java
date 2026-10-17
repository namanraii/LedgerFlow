package com.ledgerflow.ledger.service;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.BalanceHistory;
import com.ledgerflow.ledger.domain.FraudFlag;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.dto.TransactionRequest;
import com.ledgerflow.ledger.dto.TransactionResponse;
import com.ledgerflow.ledger.dto.TransferRequest;
import com.ledgerflow.ledger.dto.TransferResponse;
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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

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

        Account account = accountRepository.findByIdWithLock(req.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(req.getAccountId()));

        BigDecimal amount = money(req.getAmount());
        BigDecimal balanceBefore = account.getBalance();
        boolean isDebit = "DEBIT".equalsIgnoreCase(req.getTransactionType());

        if (isDebit && balanceBefore.compareTo(amount) < 0) {
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

        BigDecimal balanceAfter = isDebit ? balanceBefore.subtract(amount) : balanceBefore.add(amount);
        account.setBalance(money(balanceAfter));
        accountRepository.save(account);

        String status = "COMPLETED";
        if (req.getRiskScore() != null && req.getRiskScore().compareTo(FRAUD_THRESHOLD) >= 0) {
            status = "FLAGGED";
        }

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
            return transactionRepository.findByIdempotencyKey(req.getIdempotencyKey())
                    .map(existing -> {
                        TransactionResponse r = mapToResponse(existing, account.getBalance());
                        r.setAlreadyProcessed(true);
                        return r;
                    })
                    .orElseThrow(() -> dive);
        }

        balanceHistoryRepository.save(new BalanceHistory(
                account.getAccountId(),
                savedTx.getTransactionId(),
                balanceBefore,
                account.getBalance()
        ));

        if ("FLAGGED".equals(status)) {
            String reason = clip(req.getFlagReason());
            if (reason == null) {
                reason = String.format("Risk score %.2f exceeded threshold 0.70", req.getRiskScore());
            }
            fraudFlagRepository.save(new FraudFlag(savedTx.getTransactionId(), reason, req.getRiskScore()));
            log.info("Durable fraud flag created for transaction_id={} score={}", savedTx.getTransactionId(), req.getRiskScore());
        }

        log.info("Safe transaction committed: id={} account={} amount={} type={} status={} balanceBefore={} balanceAfter={}",
                savedTx.getTransactionId(), req.getAccountId(), amount, req.getTransactionType(), status, balanceBefore, account.getBalance());

        return mapToResponse(savedTx, account.getBalance());
    }

    /**
     * Posts the opposite entry for a flagged transaction an analyst has blocked.
     * The original row stays in the book; the reversal is a new audited movement.
     * Locks the account so the credit-back cannot race with a live debit.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Transaction reversePostedTransaction(Transaction original) {
        UUID reversalKey = reversalKey(original.getTransactionId());
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(reversalKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        boolean originalDebit = "DEBIT".equalsIgnoreCase(original.getTransactionType());
        String reversalType = originalDebit ? "CREDIT" : "DEBIT";
        BigDecimal amount = money(original.getAmount());

        Account account = accountRepository.findByIdWithLock(original.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(original.getAccountId()));
        BigDecimal balanceBefore = account.getBalance();
        if (!originalDebit && balanceBefore.compareTo(amount) < 0) {
            throw new InsufficientFundsException(account.getAccountId(), balanceBefore, amount, original.getTransactionId());
        }

        BigDecimal balanceAfter = originalDebit ? balanceBefore.add(amount) : balanceBefore.subtract(amount);
        account.setBalance(money(balanceAfter));
        accountRepository.save(account);

        Transaction reversal = new Transaction(
                reversalKey,
                account.getAccountId(),
                amount,
                reversalType,
                "COMPLETED",
                null,
                "OK"
        );
        reversal.setMemo(clip("Block reversal of #" + original.getTransactionId()));
        Transaction saved = transactionRepository.save(reversal);
        balanceHistoryRepository.save(new BalanceHistory(
                account.getAccountId(),
                saved.getTransactionId(),
                balanceBefore,
                account.getBalance()
        ));
        log.info("Posted block reversal id={} for original tx={} account={} amount={}",
                saved.getTransactionId(), original.getTransactionId(), account.getAccountId(), amount);
        return saved;
    }

    /**
     * Moves funds between two accounts in one database transaction.
     * Accounts are locked in ascending id order so two opposing transfers cannot deadlock.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResponse transfer(TransferRequest req) {
        if (req.getFromAccountId() == null || req.getToAccountId() == null || req.getIdempotencyKey() == null) {
            throw new IllegalArgumentException("fromAccountId, toAccountId, and idempotencyKey are required");
        }
        if (req.getFromAccountId().equals(req.getToAccountId())) {
            throw new IllegalArgumentException("A book transfer needs two different accounts");
        }
        BigDecimal amount = money(req.getAmount());
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be strictly positive");
        }

        UUID debitKey = req.getIdempotencyKey();
        UUID creditKey = creditLegKey(debitKey);
        Optional<Transaction> existingDebit = transactionRepository.findByIdempotencyKey(debitKey);
        if (existingDebit.isPresent()) {
            return replayTransfer(existingDebit.get(), creditKey, amount);
        }

        long lowId = Math.min(req.getFromAccountId(), req.getToAccountId());
        long highId = Math.max(req.getFromAccountId(), req.getToAccountId());
        Account low = accountRepository.findByIdWithLock(lowId)
                .orElseThrow(() -> new AccountNotFoundException(lowId));
        Account high = accountRepository.findByIdWithLock(highId)
                .orElseThrow(() -> new AccountNotFoundException(highId));
        Account from = low.getAccountId().equals(req.getFromAccountId()) ? low : high;
        Account to = low.getAccountId().equals(req.getToAccountId()) ? low : high;

        BigDecimal fromBefore = from.getBalance();
        if (fromBefore.compareTo(amount) < 0) {
            throw new InsufficientFundsException(from.getAccountId(), fromBefore, amount, null);
        }

        BigDecimal toBefore = to.getBalance();
        from.setBalance(money(fromBefore.subtract(amount)));
        to.setBalance(money(toBefore.add(amount)));
        accountRepository.save(from);
        accountRepository.save(to);

        String memo = clip(req.getMemo());
        if (memo == null) {
            memo = "Book transfer";
        }

        Transaction debit = leg(debitKey, from.getAccountId(), to.getAccountId(), amount, "DEBIT", memo);
        Transaction credit = leg(creditKey, to.getAccountId(), from.getAccountId(), amount, "CREDIT", memo);
        Transaction savedDebit = transactionRepository.save(debit);
        Transaction savedCredit = transactionRepository.save(credit);

        balanceHistoryRepository.save(new BalanceHistory(from.getAccountId(), savedDebit.getTransactionId(), fromBefore, from.getBalance()));
        balanceHistoryRepository.save(new BalanceHistory(to.getAccountId(), savedCredit.getTransactionId(), toBefore, to.getBalance()));

        log.info("Book transfer committed debit={} credit={} from={} to={} amount={}",
                savedDebit.getTransactionId(), savedCredit.getTransactionId(), from.getAccountId(), to.getAccountId(), amount);

        return transferResponse(debitKey, savedDebit, savedCredit, amount, from.getBalance(), to.getBalance(), false);
    }

    public static UUID reversalKey(Long transactionId) {
        return UUID.nameUUIDFromBytes(("ledgerflow-reversal:" + transactionId).getBytes(StandardCharsets.UTF_8));
    }

    private TransferResponse replayTransfer(Transaction debit, UUID creditKey, BigDecimal amount) {
        Transaction credit = transactionRepository.findByIdempotencyKey(creditKey)
                .orElseThrow(() -> new IllegalArgumentException("Transfer debit exists without its credit leg"));
        Account from = accountRepository.findById(debit.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(debit.getAccountId()));
        Account to = accountRepository.findById(credit.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(credit.getAccountId()));
        return transferResponse(debit.getIdempotencyKey(), debit, credit, amount, from.getBalance(), to.getBalance(), true);
    }

    private static Transaction leg(UUID key, Long accountId, Long counterpartyId, BigDecimal amount, String type, String memo) {
        Transaction tx = new Transaction(key, accountId, amount, type, "COMPLETED", null, "OK");
        tx.setCounterpartyAccountId(counterpartyId);
        tx.setMemo(memo);
        return tx;
    }

    private static TransferResponse transferResponse(UUID key, Transaction debit, Transaction credit, BigDecimal amount,
                                                     BigDecimal fromBalance, BigDecimal toBalance, boolean replay) {
        TransferResponse response = new TransferResponse();
        response.setIdempotencyKey(key);
        response.setDebitTransactionId(debit.getTransactionId());
        response.setCreditTransactionId(credit.getTransactionId());
        response.setFromAccountId(debit.getAccountId());
        response.setToAccountId(credit.getAccountId());
        response.setAmount(amount);
        response.setFromBalanceAfter(fromBalance);
        response.setToBalanceAfter(toBalance);
        response.setAlreadyProcessed(replay);
        return response;
    }

    private static UUID creditLegKey(UUID debitKey) {
        return UUID.nameUUIDFromBytes((debitKey + ":credit").getBytes(StandardCharsets.UTF_8));
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("amount is required");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    static String clip(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 160 ? trimmed : trimmed.substring(0, 160);
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
