package com.ledgerflow.ledger.service;

import com.ledgerflow.ledger.dto.DashboardSummary;
import com.ledgerflow.ledger.repository.AccountRepository;
import com.ledgerflow.ledger.repository.FraudFlagRepository;
import com.ledgerflow.ledger.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class DashboardService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final FraudFlagRepository fraudFlagRepository;

    public DashboardService(AccountRepository accountRepository,
                            TransactionRepository transactionRepository,
                            FraudFlagRepository fraudFlagRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.fraudFlagRepository = fraudFlagRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summarize() {
        DashboardSummary summary = new DashboardSummary();
        summary.setAssetsUnderManagement(money(accountRepository.totalAssets()));
        summary.setAccountCount(accountRepository.count());
        summary.setUnreviewedFlags(fraudFlagRepository.countByReviewedFalse());
        summary.setDebitVolume(money(transactionRepository.volumeByType("DEBIT")));
        summary.setCreditVolume(money(transactionRepository.volumeByType("CREDIT")));

        long total = 0;
        for (Object[] row : transactionRepository.countGroupedByStatus()) {
            String status = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            total += count;
            switch (status) {
                case "COMPLETED" -> summary.setCompletedCount(count);
                case "FLAGGED" -> summary.setFlaggedCount(count);
                case "REJECTED" -> summary.setRejectedCount(count);
                default -> {
                }
            }
        }
        summary.setTransactionCount(total);

        Double average = transactionRepository.averageRiskScore();
        summary.setAverageRisk(average == null
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(average).setScale(4, RoundingMode.HALF_UP));
        return summary;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
