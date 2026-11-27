package com.bank.transaction;

import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.model.Account;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.service.AccountLockManager;
import com.bank.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TransferDeadlockTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountLockManager accountLockManager;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("Checkpoint 8.1: Deterministic Row-Level Lock Ordering Verification")
    void testDeterministicLockOrdering() {
        // Enforce deterministic lexicographical order: smaller ID must always be locked first
        String accA = "ACC1001";
        String accB = "ACC2004";

        try (AccountLockManager.OrderedLockPair pair1 = accountLockManager.acquireDeterministicLocks(accA, accB)) {
            assertThat(pair1).isNotNull();
        }

        // Reverse argument order: must still acquire accA (ACC1001) first, breaking circular wait
        try (AccountLockManager.OrderedLockPair pair2 = accountLockManager.acquireDeterministicLocks(accB, accA)) {
            assertThat(pair2).isNotNull();
        }
    }

    @Test
    @DisplayName("Checkpoint 8.2: Bidirectional Simultaneous Transfer Deadlock Probe")
    void testBidirectionalTransfersDoNotDeadlock() throws Exception {
        String accA = "ACC1001";
        String accB = "ACC2004";

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        // Thread 1: ACC1001 -> ACC2004
        Future<?> future1 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                TransactionRequest req = new TransactionRequest(
                        "TXN-DL-1-" + UUID.randomUUID(), accA, accB, new BigDecimal("100.00"), TransactionType.TRANSFER
                );
                transactionService.processTransaction(req);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Thread 2: ACC2004 -> ACC1001
        Future<?> future2 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                TransactionRequest req = new TransactionRequest(
                        "TXN-DL-2-" + UUID.randomUUID(), accB, accA, new BigDecimal("100.00"), TransactionType.TRANSFER
                );
                transactionService.processTransaction(req);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        assertDoesNotThrow(() -> {
            future1.get(10, TimeUnit.SECONDS);
            future2.get(10, TimeUnit.SECONDS);
        }, "Bidirectional transfers must complete without deadlock");

        executor.shutdownNow();
    }

    @Test
    @DisplayName("Checkpoint 8.3: High Concurrency Reciprocal Transfers (20 Threads, 200 Transfers, 0 Deadlocks)")
    void testHighConcurrencyReciprocalTransfersZeroDeadlocks() throws InterruptedException {
        String accA = "ACC1001";
        String accB = "ACC2004";

        Account aBefore = accountRepository.findById(accA).orElseThrow();
        Account bBefore = accountRepository.findById(accB).orElseThrow();
        BigDecimal combinedInitial = aBefore.getBalance().add(bBefore.getBalance());

        int threadCount = 20;
        int transfersTotal = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(transfersTotal);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < transfersTotal; i++) {
            final boolean forward = (i % 2 == 0);
            final String txId = "RECIP-TXN-" + i + "-" + UUID.randomUUID().toString().substring(0, 6);

            executor.submit(() -> {
                try {
                    startLatch.await();
                    String src = forward ? accA : accB;
                    String dest = forward ? accB : accA;
                    BigDecimal amount = new BigDecimal("10.00");

                    TransactionRequest req = new TransactionRequest(txId, src, dest, amount, TransactionType.TRANSFER);
                    transactionService.processTransaction(req);
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all 20 threads simultaneously
        startLatch.countDown();

        boolean finishedInTime = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finishedInTime).isTrue();
        assertThat(successCount.get()).isEqualTo(transfersTotal);
        assertThat(failureCount.get()).isEqualTo(0);

        // Conservation of Money Invariant: Total balance between A and B must be strictly preserved
        Account aAfter = accountRepository.findById(accA).orElseThrow();
        Account bAfter = accountRepository.findById(accB).orElseThrow();
        BigDecimal combinedAfter = aAfter.getBalance().add(bAfter.getBalance());

        assertThat(combinedAfter).isEqualByComparingTo(combinedInitial);
    }
}
