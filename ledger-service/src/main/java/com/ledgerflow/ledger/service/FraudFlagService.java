package com.ledgerflow.ledger.service;

import com.ledgerflow.ledger.domain.FraudFlag;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.dto.FlaggedReportRow;
import com.ledgerflow.ledger.dto.FraudFlagRequest;
import com.ledgerflow.ledger.dto.ReviewFlagRequest;
import com.ledgerflow.ledger.repository.FlaggedReportQuery;
import com.ledgerflow.ledger.repository.FraudFlagRepository;
import com.ledgerflow.ledger.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FraudFlagService {
    private static final Logger log = LoggerFactory.getLogger(FraudFlagService.class);

    private final FraudFlagRepository fraudFlagRepository;
    private final TransactionRepository transactionRepository;
    private final FlaggedReportQuery flaggedReportQuery;
    private final SafeLedgerService safeLedgerService;

    public FraudFlagService(FraudFlagRepository fraudFlagRepository,
                            TransactionRepository transactionRepository,
                            FlaggedReportQuery flaggedReportQuery,
                            SafeLedgerService safeLedgerService) {
        this.fraudFlagRepository = fraudFlagRepository;
        this.transactionRepository = transactionRepository;
        this.flaggedReportQuery = flaggedReportQuery;
        this.safeLedgerService = safeLedgerService;
    }

    @Transactional
    public FraudFlag createFraudFlag(FraudFlagRequest req) {
        FraudFlag flag = new FraudFlag(req.getTransactionId(), req.getReason(), req.getRiskScore());
        FraudFlag saved = fraudFlagRepository.save(flag);
        log.info("Created durable fraud flag id={} for tx={}", saved.getId(), req.getTransactionId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<FraudFlag> getUnreviewedFlags() {
        return fraudFlagRepository.findByReviewedFalseOrderByRiskScoreDesc();
    }

    @Transactional(readOnly = true)
    public List<FlaggedReportRow> getFlaggedReport() {
        return flaggedReportQuery.unreviewed();
    }

    @Transactional
    public FraudFlag reviewFlag(Long flagId, ReviewFlagRequest req) {
        FraudFlag flag = fraudFlagRepository.findById(flagId)
                .orElseThrow(() -> new IllegalArgumentException("Fraud flag not found: " + flagId));
        if (Boolean.TRUE.equals(flag.getReviewed())) {
            throw new IllegalArgumentException("Fraud flag " + flagId + " has already been reviewed");
        }

        String action = req.getReviewerAction().toUpperCase();
        Transaction tx = transactionRepository.findById(flag.getTransactionId())
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found for flag " + flagId));

        if ("BLOCKED".equals(action)) {
            safeLedgerService.reversePostedTransaction(tx);
            tx.setStatus("REJECTED");
            if (tx.getMemo() == null || tx.getMemo().isBlank()) {
                tx.setMemo(SafeLedgerService.clip("Blocked by analyst"));
            }
        } else if ("APPROVED".equals(action)) {
            tx.setStatus("COMPLETED");
        } else {
            throw new IllegalArgumentException("reviewerAction must be APPROVED or BLOCKED");
        }

        flag.setReviewed(true);
        flag.setReviewerAction(action);
        transactionRepository.save(tx);
        FraudFlag updated = fraudFlagRepository.save(flag);
        log.info("Fraud flag id={} reviewed with action={}", flagId, action);
        return updated;
    }
}
