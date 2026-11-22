package com.bank.transaction.repository;

import com.bank.transaction.model.FailedTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FailedTransactionRepository extends JpaRepository<FailedTransaction, Long> {

    List<FailedTransaction> findByTransactionId(String transactionId);

    @Query("SELECT f FROM FailedTransaction f ORDER BY f.failedAt DESC")
    List<FailedTransaction> findRecent(Pageable pageable);
}
