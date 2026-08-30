package com.ledgerflow.ledger.repository;

import com.ledgerflow.ledger.domain.BalanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BalanceHistoryRepository extends JpaRepository<BalanceHistory, Long> {
    List<BalanceHistory> findByAccountIdOrderByCreatedAtDesc(Long accountId);
}
