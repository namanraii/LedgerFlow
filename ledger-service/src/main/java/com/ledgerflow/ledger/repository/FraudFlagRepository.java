package com.ledgerflow.ledger.repository;

import com.ledgerflow.ledger.domain.FraudFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FraudFlagRepository extends JpaRepository<FraudFlag, Long> {
    List<FraudFlag> findByReviewedFalseOrderByRiskScoreDesc();

    // Section 10 Reporting Query with Window Function RANK() OVER (PARTITION BY account_id ORDER BY risk_score DESC)
    @Query(value = """
        SELECT
            a.account_holder AS accountHolder,
            t.transaction_id AS transactionId,
            t.account_id AS accountId,
            t.amount AS amount,
            f.risk_score AS riskScore,
            f.reason AS reason,
            RANK() OVER (PARTITION BY t.account_id ORDER BY f.risk_score DESC) AS riskRank,
            f.id AS flagId,
            f.created_at AS createdAt
        FROM fraud_flags f
        JOIN transactions t ON t.transaction_id = f.transaction_id
        JOIN accounts a ON a.account_id = t.account_id
        WHERE f.reviewed = FALSE
        ORDER BY f.risk_score DESC
        """, nativeQuery = true)
    List<FlaggedReportProjection> getUnreviewedFlaggedReport();

    interface FlaggedReportProjection {
        String getAccountHolder();
        Long getTransactionId();
        Long getAccountId();
        java.math.BigDecimal getAmount();
        java.math.BigDecimal getRiskScore();
        String getReason();
        Long getRiskRank();
        Long getFlagId();
        java.time.LocalDateTime getCreatedAt();
    }
}
