package com.ledgerflow.ledger.repository;

import com.ledgerflow.ledger.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :id")
    Optional<Account> findByIdWithLock(@Param("id") Long id);

    Optional<Account> findByAccountHolder(String accountHolder);

    List<Account> findAllByOrderByAccountIdAsc();

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a")
    BigDecimal totalAssets();
}
