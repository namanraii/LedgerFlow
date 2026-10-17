package com.ledgerflow.ledger.repository;

import com.ledgerflow.ledger.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);

    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    Page<Transaction> findByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT t.status, COUNT(t) FROM Transaction t GROUP BY t.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.transactionType = :type AND t.status <> 'REJECTED'")
    BigDecimal volumeByType(@Param("type") String type);

    @Query("SELECT AVG(t.riskScore) FROM Transaction t WHERE t.riskScore IS NOT NULL")
    Double averageRiskScore();
}
