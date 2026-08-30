package com.ledgerflow.ledger.service;

import com.ledgerflow.ledger.domain.FraudFlag;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.dto.FraudFlagRequest;
import com.ledgerflow.ledger.dto.ReviewFlagRequest;
import com.ledgerflow.ledger.exception.InvalidRequestException;
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

    public FraudFlagService(FraudFlagRepository fraudFlagRepository, TransactionRepository transactionRepository) {
        this.fraudFlagRepository = fraudFlagRepository;
        this.transactionRepository = transactionRepository;
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
    public List<FraudFlagRepository.FlaggedReportProjection> getFlaggedReport() {
        return fraudFlagRepository.getUnreviewedFlaggedReport();
    }

    @Transactional
    public FraudFlag reviewFlag(Long flagId, ReviewFlagRequest req) {
        FraudFlag flag = fraudFlagRepository.findById(flagId)
                .orElseThrow(() -> new IllegalArgumentException("Fraud flag not found: " + flagId));

        flag.setReviewed(true);
        flag.setReviewerAction(req.getReviewerAction());
        FraudFlag updated = fraudFlagRepository.save(flag);

        // Update transaction status accordingly if BLOCKED
        transactionRepository.findById(flag.getTransactionId()).ifPresent(tx -> {
            if ("BLOCKED".equalsIgnoreCase(req.getReviewerAction())) {
                tx.setStatus("REJECTED");
            } else if ("APPROVED".equalsIgnoreCase(req.getReviewerAction())) {
                tx.setStatus("COMPLETED");
            }
            transactionRepository.save(tx);
        });

        log.info("Fraud flag id={} reviewed with action={}", flagId, req.getReviewerAction());
        return updated;
    }
}
