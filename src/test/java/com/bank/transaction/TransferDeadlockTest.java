package com.bank.transaction;

import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class TransferDeadlockTest {

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("Deterministic Locking: Concurrent bidirectional transfers (A->B and B->A) must not deadlock")
    void testBidirectionalTransfersDoNotDeadlock() throws InterruptedException, ExecutionException, TimeoutException {
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
}
