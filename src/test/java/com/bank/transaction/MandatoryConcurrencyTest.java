package com.bank.transaction;

import com.bank.transaction.dto.ConcurrencyTestResult;
import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.model.Account;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.service.ConcurrencyEngine;
import com.bank.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MandatoryConcurrencyTest {

    @Autowired
    private ConcurrencyEngine concurrencyEngine;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("Checkpoint 9.1: Mandatory Scenario - Account ₹10,000 with concurrent ₹7,000 & ₹5,000 withdrawals must not both succeed")
    void testConcurrentWithdrawalsPreserveBalanceInvariant() throws InterruptedException {
        ConcurrencyTestResult result = concurrencyEngine.runMandatoryConcurrencyScenario();

        // 1. Invariant must be satisfied
        assertTrue(result.invariantSatisfied(), "Concurrency invariant failed: " + result.summary());

        // 2. Final balance must be non-negative
        assertTrue(result.finalBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Final balance cannot be negative: " + result.finalBalance());

        // 3. Exactly one thread must have succeeded
        long successes = result.threadDetails().stream()
                .filter(t -> "SUCCESS".equalsIgnoreCase(t.status()))
                .count();
        assertEquals(1, successes, "Exactly one withdrawal must succeed");

        // 4. Expected final balance must be either 3000 (if 7000 succeeded) or 5000 (if 5000 succeeded)
        boolean validFinalBalance = result.finalBalance().compareTo(new BigDecimal("3000.00")) == 0 ||
                                    result.finalBalance().compareTo(new BigDecimal("5000.00")) == 0;
        assertTrue(validFinalBalance, "Final balance must be exactly ₹3,000 or ₹5,000, but was: " + result.finalBalance());
    }

    @RepeatedTest(5)
    @DisplayName("Checkpoint 9.2: Stress Invariant Check - Repeat concurrent withdrawal test 5 times consecutively")
    void testRepeatedConcurrentWithdrawals() throws InterruptedException {
        ConcurrencyTestResult result = concurrencyEngine.runMandatoryConcurrencyScenario();
        assertTrue(result.invariantSatisfied());
        assertTrue(result.finalBalance().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    @DisplayName("Checkpoint 9.3: Concurrent Multi-Thread Deposit & Withdrawal on Same Account (Zero Lost Updates)")
    void testConcurrentDepositAndWithdrawalZeroLostUpdates() throws InterruptedException {
        String testAccount = "ACC1001";

        // Reset account balance to ₹10,000.00
        Account account = accountRepository.findById(testAccount).orElseThrow();
        account.setBalance(new BigDecimal("10000.00"));
        accountRepository.saveAndFlush(account);

        int operationsPerType = 10;
        int totalOps = operationsPerType * 2; // 10 deposits + 10 withdrawals of ₹100
        ExecutorService executor = Executors.newFixedThreadPool(totalOps);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalOps);

        AtomicInteger successfulOps = new AtomicInteger(0);

        // Submit 10 deposits of ₹100.00
        for (int i = 0; i < operationsPerType; i++) {
            final String txnId = "DEP-RACE-" + i + "-" + UUID.randomUUID().toString().substring(0, 6);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransactionRequest req = new TransactionRequest(
                            txnId, null, testAccount, new BigDecimal("100.00"), TransactionType.DEPOSIT
                    );
                    transactionService.processTransaction(req);
                    successfulOps.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Submit 10 withdrawals of ₹100.00
        for (int i = 0; i < operationsPerType; i++) {
            final String txnId = "WTH-RACE-" + i + "-" + UUID.randomUUID().toString().substring(0, 6);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransactionRequest req = new TransactionRequest(
                            txnId, testAccount, null, new BigDecimal("100.00"), TransactionType.WITHDRAWAL
                    );
                    transactionService.processTransaction(req);
                    successfulOps.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all 20 threads simultaneously
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(finished, "Operations should finish within timeout");
        assertEquals(totalOps, successfulOps.get(), "All 20 concurrent operations should succeed");

        // Net balance change must be exactly 0: 10,000 + (10 * 100) - (10 * 100) = 10,000
        Account finalAccount = accountRepository.findById(testAccount).orElseThrow();
        assertEquals(new BigDecimal("10000.00"), finalAccount.getBalance(),
                "Final balance must remain exactly ₹10,000 after balanced concurrent operations (zero lost updates)");
    }
}
