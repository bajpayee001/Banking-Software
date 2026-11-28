package com.bank.transaction;

import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.exception.DuplicateTransactionException;
import com.bank.transaction.model.Account;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IdempotencyTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("Checkpoint 9.4: Sequential Duplicate Submission Rejected Without Double-Processing")
    void testRepeatedTransactionIdRejectedWithoutDuplicateImpact() {
        String testTxnId = "TXN-IDEMP-" + UUID.randomUUID();
        String targetAccount = "ACC2004";

        Account beforeAcc = accountRepository.findById(targetAccount).orElseThrow();
        BigDecimal initialBalance = beforeAcc.getBalance();
        BigDecimal depositAmount = new BigDecimal("2500.00");

        // 1. First execution - Must succeed
        TransactionRequest req1 = new TransactionRequest(
                testTxnId, null, targetAccount, depositAmount, TransactionType.DEPOSIT
        );
        TransactionResponse resp1 = transactionService.processTransaction(req1);
        assertNotNull(resp1);
        assertEquals(initialBalance.add(depositAmount), resp1.remainingDestinationBalance());

        // Verify balance in database updated once
        Account intermediateAcc = accountRepository.findById(targetAccount).orElseThrow();
        assertEquals(initialBalance.add(depositAmount), intermediateAcc.getBalance());

        // 2. Second execution with identical transactionId - Must fail with DuplicateTransactionException
        TransactionRequest duplicateReq = new TransactionRequest(
                testTxnId, null, targetAccount, depositAmount, TransactionType.DEPOSIT
        );

        assertThrows(DuplicateTransactionException.class, () -> {
            transactionService.processTransaction(duplicateReq);
        });

        // 3. Verify balance in database was NOT incremented a second time
        Account finalAcc = accountRepository.findById(targetAccount).orElseThrow();
        assertEquals(initialBalance.add(depositAmount), finalAcc.getBalance(),
                "Balance must remain unchanged after duplicate transaction request");
    }

    @Test
    @DisplayName("Checkpoint 9.5: Concurrent Duplicate Transactions - Racing Identical TxnId Only Executes Once")
    void testConcurrentIdenticalTxnIdExecutesExactlyOnce() throws InterruptedException {
        String testTxnId = "TXN-CONC-DUP-" + UUID.randomUUID();
        String targetAccount = "ACC2004";

        Account beforeAcc = accountRepository.findById(targetAccount).orElseThrow();
        BigDecimal initialBalance = beforeAcc.getBalance();
        BigDecimal depositAmount = new BigDecimal("1000.00");

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger duplicateRejections = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransactionRequest req = new TransactionRequest(
                            testTxnId, null, targetAccount, depositAmount, TransactionType.DEPOSIT
                    );
                    transactionService.processTransaction(req);
                    successes.incrementAndGet();
                } catch (DuplicateTransactionException ex) {
                    duplicateRejections.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release both threads simultaneously to race on identical transactionId
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(completed);
        assertEquals(1, successes.get(), "Exactly one racing thread must succeed");
        assertEquals(1, duplicateRejections.get(), "The other racing thread must be rejected with DuplicateTransactionException");

        // Verify balance in database was credited exactly once (+1000, not +2000)
        Account afterAcc = accountRepository.findById(targetAccount).orElseThrow();
        assertEquals(initialBalance.add(depositAmount), afterAcc.getBalance(),
                "Balance must only increase by 1000, never duplicate crediting");
    }
}
