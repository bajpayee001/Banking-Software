package com.bank.transaction.service;

import com.bank.transaction.dto.ConcurrencyTestResult;
import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.model.Account;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ConcurrencyEngine {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyEngine.class);

    private final TransactionService transactionService;
    private final AccountRepository accountRepository;
    private final ExecutorService transactionExecutor;

    public ConcurrencyEngine(TransactionService transactionService,
                             AccountRepository accountRepository,
                             @Qualifier("transactionExecutor") ExecutorService transactionExecutor) {
        this.transactionService = transactionService;
        this.accountRepository = accountRepository;
        this.transactionExecutor = transactionExecutor;
    }

    /**
     * Executes the mandatory concurrency invariant scenario:
     * - Account ACC1001 initialized with ₹10,000
     * - Thread A requests withdrawal of ₹7,000
     * - Thread B requests withdrawal of ₹5,000
     * - A CountDownLatch releases both threads simultaneously.
     * - Invariant: Exactly ONE transaction succeeds, the other fails with InsufficientBalanceException.
     * - Final balance is strictly non-negative (either ₹3,000 or ₹5,000, never negative!).
     */
    public ConcurrencyTestResult runMandatoryConcurrencyScenario() throws InterruptedException {
        String testAccountId = "ACC1001";

        // Reset account balance to exactly ₹10,000.00 for consistent reproducible test
        Account account = accountRepository.findById(testAccountId)
                .orElseThrow(() -> new RuntimeException("Account " + testAccountId + " not found"));
        account.setBalance(new BigDecimal("10000.00"));
        accountRepository.saveAndFlush(account);

        BigDecimal openingBalance = new BigDecimal("10000.00");
        String txnAId = "TXN-CONC-A-" + UUID.randomUUID().toString().substring(0, 8);
        String txnBId = "TXN-CONC-B-" + UUID.randomUUID().toString().substring(0, 8);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<ConcurrencyTestResult.ThreadExecutionDetail> executionDetails =
                Collections.synchronizedList(new ArrayList<>());

        // Thread A task: Withdraw 7,000
        Callable<Void> taskA = () -> {
            readyLatch.countDown();
            startLatch.await();
            long start = System.currentTimeMillis();
            try {
                TransactionRequest req = new TransactionRequest(
                        txnAId, testAccountId, null, new BigDecimal("7000.00"), TransactionType.WITHDRAWAL
                );
                TransactionResponse resp = transactionService.processTransaction(req);
                long duration = System.currentTimeMillis() - start;
                executionDetails.add(new ConcurrencyTestResult.ThreadExecutionDetail(
                        Thread.currentThread().getName(), txnAId, req.getAmount(), "SUCCESS",
                        "Withdrawal approved. Remaining balance: " + resp.remainingSourceBalance(), duration
                ));
            } catch (Exception ex) {
                long duration = System.currentTimeMillis() - start;
                executionDetails.add(new ConcurrencyTestResult.ThreadExecutionDetail(
                        Thread.currentThread().getName(), txnAId, new BigDecimal("7000.00"), "REJECTED/FAILED",
                        ex.getMessage(), duration
                ));
            }
            return null;
        };

        // Thread B task: Withdraw 5,000
        Callable<Void> taskB = () -> {
            readyLatch.countDown();
            startLatch.await();
            long start = System.currentTimeMillis();
            try {
                TransactionRequest req = new TransactionRequest(
                        txnBId, testAccountId, null, new BigDecimal("5000.00"), TransactionType.WITHDRAWAL
                );
                TransactionResponse resp = transactionService.processTransaction(req);
                long duration = System.currentTimeMillis() - start;
                executionDetails.add(new ConcurrencyTestResult.ThreadExecutionDetail(
                        Thread.currentThread().getName(), txnBId, req.getAmount(), "SUCCESS",
                        "Withdrawal approved. Remaining balance: " + resp.remainingSourceBalance(), duration
                ));
            } catch (Exception ex) {
                long duration = System.currentTimeMillis() - start;
                executionDetails.add(new ConcurrencyTestResult.ThreadExecutionDetail(
                        Thread.currentThread().getName(), txnBId, new BigDecimal("5000.00"), "REJECTED/FAILED",
                        ex.getMessage(), duration
                ));
            }
            return null;
        };

        Future<Void> futureA = transactionExecutor.submit(taskA);
        Future<Void> futureB = transactionExecutor.submit(taskB);

        // Wait until both worker threads are ready, then fire simultaneously
        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        try {
            futureA.get(10, TimeUnit.SECONDS);
            futureB.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            log.error("Error awaiting concurrency execution: {}", e.getMessage());
        }

        // Verify final state
        Account updatedAccount = accountRepository.findById(testAccountId).orElseThrow();
        BigDecimal finalBalance = updatedAccount.getBalance();

        long successCount = executionDetails.stream().filter(d -> "SUCCESS".equals(d.status())).count();
        boolean invariantSatisfied = (successCount == 1) && (finalBalance.compareTo(BigDecimal.ZERO) >= 0);

        String summary = invariantSatisfied
                ? "INVARIANT PROVEN: Opening balance ₹10,000 with concurrent requests ₹7,000 and ₹5,000 resulted in exactly ONE success and ONE rejection. Final balance is ₹" + finalBalance + " (>= 0)."
                : "INVARIANT VIOLATION DETECTED: Success count=" + successCount + ", Final balance=₹" + finalBalance;

        log.info("[CONCURRENCY-TEST-RESULT] {}", summary);

        return new ConcurrencyTestResult(
                testAccountId,
                openingBalance,
                finalBalance,
                invariantSatisfied,
                summary,
                executionDetails
        );
    }
}
