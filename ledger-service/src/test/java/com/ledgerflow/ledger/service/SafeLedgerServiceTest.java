package com.ledgerflow.ledger.service;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.FraudFlag;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.dto.TransactionRequest;
import com.ledgerflow.ledger.dto.TransactionResponse;
import com.ledgerflow.ledger.exception.InsufficientFundsException;
import com.ledgerflow.ledger.repository.AccountRepository;
import com.ledgerflow.ledger.repository.BalanceHistoryRepository;
import com.ledgerflow.ledger.repository.FraudFlagRepository;
import com.ledgerflow.ledger.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SafeLedgerServiceTest {

    @Autowired
    private SafeLedgerService safeLedgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BalanceHistoryRepository balanceHistoryRepository;

    @Autowired
    private FraudFlagRepository fraudFlagRepository;

    private Long testAccountId;

    @BeforeEach
    void setUp() {
        Account account = new Account("Test User", new BigDecimal("1000.00"));
        Account saved = accountRepository.save(account);
        testAccountId = saved.getAccountId();
    }

    @Test
    void testSuccessfulDebitAndBalanceMutation() {
        TransactionRequest req = new TransactionRequest();
        req.setIdempotencyKey(UUID.randomUUID());
        req.setAccountId(testAccountId);
        req.setAmount(new BigDecimal("250.00"));
        req.setTransactionType("DEBIT");
        req.setRiskScore(new BigDecimal("0.15"));

        TransactionResponse resp = safeLedgerService.processTransaction(req);

        assertNotNull(resp.getTransactionId());
        assertEquals("COMPLETED", resp.getStatus());
        assertEquals(new BigDecimal("750.00"), resp.getBalanceAfter());
        assertFalse(resp.isAlreadyProcessed());

        Account updatedAccount = accountRepository.findById(testAccountId).orElseThrow();
        assertEquals(new BigDecimal("750.00"), updatedAccount.getBalance());

        // Verify balance history recorded
        var history = balanceHistoryRepository.findByAccountIdOrderByCreatedAtDesc(testAccountId);
        assertFalse(history.isEmpty());
        assertEquals(new BigDecimal("1000.00"), history.get(0).getBalanceBefore());
        assertEquals(new BigDecimal("750.00"), history.get(0).getBalanceAfter());
    }

    @Test
    void testInsufficientFundsThrowsExceptionAndPreservesAuditRecord() {
        TransactionRequest req = new TransactionRequest();
        UUID key = UUID.randomUUID();
        req.setIdempotencyKey(key);
        req.setAccountId(testAccountId);
        req.setAmount(new BigDecimal("1500.00")); // More than 1000.00
        req.setTransactionType("DEBIT");

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class, () -> {
            safeLedgerService.processTransaction(req);
        });

        assertEquals(testAccountId, ex.getAccountId());
        assertEquals(new BigDecimal("1000.00"), ex.getCurrentBalance());

        // Assert account balance remains unchanged
        Account account = accountRepository.findById(testAccountId).orElseThrow();
        assertEquals(new BigDecimal("1000.00"), account.getBalance());

        // Assert audit record exists with status REJECTED
        Transaction rejectedTx = transactionRepository.findByIdempotencyKey(key).orElseThrow();
        assertEquals("REJECTED", rejectedTx.getStatus());
    }

    @Test
    void testIdempotencyDuplicateRequestReturnsOriginalResult() {
        UUID sharedKey = UUID.randomUUID();

        TransactionRequest req1 = new TransactionRequest();
        req1.setIdempotencyKey(sharedKey);
        req1.setAccountId(testAccountId);
        req1.setAmount(new BigDecimal("100.00"));
        req1.setTransactionType("DEBIT");

        TransactionResponse resp1 = safeLedgerService.processTransaction(req1);
        assertEquals(new BigDecimal("900.00"), resp1.getBalanceAfter());
        assertFalse(resp1.isAlreadyProcessed());

        // Duplicate submission with same key
        TransactionRequest req2 = new TransactionRequest();
        req2.setIdempotencyKey(sharedKey);
        req2.setAccountId(testAccountId);
        req2.setAmount(new BigDecimal("100.00"));
        req2.setTransactionType("DEBIT");

        TransactionResponse resp2 = safeLedgerService.processTransaction(req2);
        assertEquals(resp1.getTransactionId(), resp2.getTransactionId());
        assertTrue(resp2.isAlreadyProcessed());

        // Assert balance deducted only once
        Account account = accountRepository.findById(testAccountId).orElseThrow();
        assertEquals(new BigDecimal("900.00"), account.getBalance());
    }

    @Test
    void testFlaggedTransactionPersistsDurableFlag() {
        TransactionRequest req = new TransactionRequest();
        req.setIdempotencyKey(UUID.randomUUID());
        req.setAccountId(testAccountId);
        req.setAmount(new BigDecimal("500.00"));
        req.setTransactionType("DEBIT");
        req.setRiskScore(new BigDecimal("0.85")); // High risk >= 0.70

        TransactionResponse resp = safeLedgerService.processTransaction(req);
        assertEquals("FLAGGED", resp.getStatus());

        List<FraudFlag> unreviewed = fraudFlagRepository.findByReviewedFalseOrderByRiskScoreDesc();
        boolean flagFound = unreviewed.stream().anyMatch(f -> f.getTransactionId().equals(resp.getTransactionId()));
        assertTrue(flagFound, "Durable fraud flag must exist for flagged transaction");
    }
}
