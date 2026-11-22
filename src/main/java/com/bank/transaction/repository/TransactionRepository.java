package com.bank.transaction.repository;

import com.bank.transaction.model.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount.accountId = :accountId OR t.destinationAccount.accountId = :accountId ORDER BY t.createdAt DESC")
    List<Transaction> findRecentByAccount(@Param("accountId") String accountId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount.customer.customerId = :customerId OR t.destinationAccount.customer.customerId = :customerId ORDER BY t.createdAt DESC")
    List<Transaction> findRecentByCustomer(@Param("customerId") String customerId, Pageable pageable);

    @Query("SELECT t FROM Transaction t ORDER BY t.createdAt DESC")
    List<Transaction> findTopRecent(Pageable pageable);
}
