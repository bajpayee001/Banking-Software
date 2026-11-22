package com.bank.transaction.repository;

import com.bank.transaction.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByCustomerCustomerId(String customerId);

    /**
     * Pessimistic Write Lock: Acquires row-level lock using 'SELECT ... FOR UPDATE'.
     * Prevents dirty reads, non-repeatable reads, and lost updates across concurrent threads.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
    Optional<Account> findWithLockByAccountId(@Param("accountId") String accountId);

    /**
     * Atomic SQL update variant: guaranteed to execute atomically at the database engine level.
     * Prevents negative balance check violations directly via SQL predicate 'balance >= :amount'.
     */
    @Modifying
    @Query("UPDATE Account a SET a.balance = a.balance - :amount, a.updatedAt = CURRENT_TIMESTAMP WHERE a.accountId = :accountId AND a.balance >= :amount")
    int debitAtomic(@Param("accountId") String accountId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE Account a SET a.balance = a.balance + :amount, a.updatedAt = CURRENT_TIMESTAMP WHERE a.accountId = :accountId")
    int creditAtomic(@Param("accountId") String accountId, @Param("amount") BigDecimal amount);
}
