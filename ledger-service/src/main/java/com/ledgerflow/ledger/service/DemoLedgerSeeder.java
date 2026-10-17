package com.ledgerflow.ledger.service;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.dto.TransactionRequest;
import com.ledgerflow.ledger.repository.AccountRepository;
import com.ledgerflow.ledger.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Opens the book with a short, reconciled history so the desk is not an empty shell.
 * Each leg uses a fixed idempotency key and is skipped once it exists.
 * The benchmark account is left untouched.
 */
@Component
public class DemoLedgerSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoLedgerSeeder.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final SafeLedgerService safeLedgerService;

    public DemoLedgerSeeder(AccountRepository accountRepository,
                            TransactionRepository transactionRepository,
                            SafeLedgerService safeLedgerService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.safeLedgerService = safeLedgerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int posted = 0;
        posted += post("Alice Smith (Institutional)", "a1000000-0000-4000-8000-000000000001", "DEBIT", "2400.00", "0.1800", null);
        posted += post("Alice Smith (Institutional)", "a1000000-0000-4000-8000-000000000002", "CREDIT", "5000.00", "0.0800", null);
        posted += post("Alice Smith (Institutional)", "a1000000-0000-4000-8000-000000000003", "DEBIT", "2600.00", "0.9100",
                "Wire to an unverified beneficiary");

        posted += post("Bob Jones (Retail)", "b2000000-0000-4000-8000-000000000001", "CREDIT", "1200.00", "0.1100", null);
        posted += post("Bob Jones (Retail)", "b2000000-0000-4000-8000-000000000002", "DEBIT", "900.00", "0.7400",
                "Round-dollar withdrawal above the velocity band");
        posted += post("Bob Jones (Retail)", "b2000000-0000-4000-8000-000000000003", "DEBIT", "300.00", "0.1600", null);

        posted += post("Charlie Brown (High-Net-Worth)", "c3000000-0000-4000-8000-000000000001", "DEBIT", "8000.00", "0.5500", null);
        posted += post("Charlie Brown (High-Net-Worth)", "c3000000-0000-4000-8000-000000000002", "CREDIT", "15000.00", "0.9200",
                "Inbound credit from a new counterparty");
        posted += post("Charlie Brown (High-Net-Worth)", "c3000000-0000-4000-8000-000000000003", "DEBIT", "7000.00", "0.2200", null);

        if (posted > 0) {
            log.info("Seeded {} opening ledger entries", posted);
        }
    }

    private int post(String holder, String key, String type, String amount, String risk, String reason) {
        UUID idempotencyKey = UUID.fromString(key);
        if (transactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return 0;
        }
        Account account = accountRepository.findByAccountHolder(holder).orElse(null);
        if (account == null) {
            log.warn("Skipping seed leg {}: account '{}' is not on the book", key, holder);
            return 0;
        }
        TransactionRequest request = new TransactionRequest();
        request.setIdempotencyKey(idempotencyKey);
        request.setAccountId(account.getAccountId());
        request.setAmount(new BigDecimal(amount));
        request.setTransactionType(type);
        request.setRiskScore(new BigDecimal(risk));
        request.setFraudCheckStatus("OK");
        request.setFlagReason(reason);
        request.setSafe(true);
        safeLedgerService.processTransaction(request);
        return 1;
    }
}
