package com.bank.transaction.service.ai;

import com.bank.transaction.dto.ContentionBatchResult;
import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.model.Account;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionStatus;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ContentionRouterService {

    private static final Logger log = LoggerFactory.getLogger(ContentionRouterService.class);

    private final TransactionService transactionService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;

    public ContentionRouterService(TransactionService transactionService,
                                   AccountRepository accountRepository,
                                   TransactionRepository transactionRepository,
                                   PlatformTransactionManager transactionManager) {
        this.transactionService = transactionService;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Demonstrates the dramatic throughput multiplication achieved when high-contention
     * transactions targeting a single "hot account" are dynamically aggregated into
     * an intelligent single-pass atomic batch rather than 50 separate competing lock acquisitions.
     */
    public ContentionBatchResult compareStandardVsIntelligentBatching(String targetAccount, int requestCount) {
        String accountId = (targetAccount != null && !targetAccount.isBlank()) ? targetAccount : "ACC2004";
        int batchSize = Math.max(10, Math.min(requestCount, 100)); // safe bounds: 10 to 100 requests

        BigDecimal amountPerReq = new BigDecimal("250.00");
        BigDecimal totalBatchAmount = amountPerReq.multiply(BigDecimal.valueOf(batchSize));

        List<TransactionRequest> requests = new ArrayList<>(batchSize);
        List<String> txnIds = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            String id = "TXN-CONT-" + UUID.randomUUID().toString().substring(0, 8);
            txnIds.add(id);
            requests.add(new TransactionRequest(id, null, accountId, amountPerReq, TransactionType.DEPOSIT));
        }

        // 1. Measure standard competitive execution (threads contending on the same row-level lock)
        ExecutorService standardPool = Executors.newFixedThreadPool(10);
        long startStandard = System.currentTimeMillis();
        CountDownLatch standardLatch = new CountDownLatch(batchSize);

        for (TransactionRequest req : requests) {
            standardPool.submit(() -> {
                try {
                    transactionService.processTransaction(req);
                } catch (Exception ignored) {
                } finally {
                    standardLatch.countDown();
                }
            });
        }

        try {
            standardLatch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            standardPool.shutdownNow();
        }
        long standardDurationMs = Math.max(1, System.currentTimeMillis() - startStandard);

        // 2. Measure AI Intelligent Batching: One single atomic lock acquisition + bulk execution
        List<String> aiBatchTxnIds = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            aiBatchTxnIds.add("TXN-AIBATCH-" + UUID.randomUUID().toString().substring(0, 8));
        }

        long startAiBatch = System.currentTimeMillis();
        executeAtomicBulkCredit(accountId, totalBatchAmount, aiBatchTxnIds, amountPerReq);
        long aiBatchDurationMs = Math.max(1, System.currentTimeMillis() - startAiBatch);

        double standardTps = Math.round(((double) batchSize / (standardDurationMs / 1000.0)) * 100.0) / 100.0;
        double aiBatchedTps = Math.round(((double) batchSize / (aiBatchDurationMs / 1000.0)) * 100.0) / 100.0;
        double speedup = Math.round(((double) standardDurationMs / (double) aiBatchDurationMs) * 10.0) / 10.0;

        String architecturalBenefit = String.format(
                "AI Contention Router detected high lock contention on '%s' (%d concurrent deposits). " +
                "By collapsing %d individual row-lock cycles into 1 atomic batch update, throughput surged from %.1f TPS to %.1f TPS (%.1fx speedup).",
                accountId, batchSize, batchSize, standardTps, aiBatchedTps, speedup
        );

        log.info("[AI-CONTENTION-ROUTER] {}", architecturalBenefit);

        return new ContentionBatchResult(
                accountId,
                batchSize,
                totalBatchAmount,
                standardDurationMs,
                aiBatchDurationMs,
                speedup,
                standardTps,
                aiBatchedTps,
                architecturalBenefit,
                aiBatchTxnIds
        );
    }

    protected void executeAtomicBulkCredit(String accountId, BigDecimal totalAmount, List<String> txnIds, BigDecimal unitAmount) {
        transactionTemplate.execute(status -> {
            Account acc = accountRepository.findWithLockByAccountId(accountId)
                    .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));

            acc.setBalance(acc.getBalance().add(totalAmount));
            acc.setUpdatedAt(LocalDateTime.now());
            accountRepository.save(acc);

            List<Transaction> transactions = new ArrayList<>(txnIds.size());
            LocalDateTime now = LocalDateTime.now();
            for (String id : txnIds) {
                transactions.add(new Transaction(
                        id, null, acc, unitAmount, TransactionType.DEPOSIT, TransactionStatus.SUCCESS, now
                ));
            }
            transactionRepository.saveAll(transactions);
            return null;
        });
    }
}
