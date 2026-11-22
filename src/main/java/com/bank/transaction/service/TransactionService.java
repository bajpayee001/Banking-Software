package com.bank.transaction.service;

import com.bank.transaction.dto.FraudAssessmentResult;
import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.exception.*;
import com.bank.transaction.model.*;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.ai.FraudDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ValidationService validationService;
    private final AccountLockManager lockManager;
    private final AuditService auditService;
    private final FraudDetectionService fraudService;
    private final TransactionTemplate transactionTemplate;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              ValidationService validationService,
                              AccountLockManager lockManager,
                              AuditService auditService,
                              FraudDetectionService fraudService,
                              PlatformTransactionManager transactionManager) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.validationService = validationService;
        this.lockManager = lockManager;
        this.auditService = auditService;
        this.fraudService = fraudService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * Executes the complete safe transaction lifecycle:
     * 1. Receive -> 2. Validate -> 3. Check Idempotency -> 4. Begin + Deterministic Lock ->
     * 5. Apply Operation -> 6. Audit + Commit -> 7. Respond.
     */
    public TransactionResponse processTransaction(TransactionRequest request) {
        long startTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        String txId = request != null ? request.getTransactionId() : "UNKNOWN";

        log.info("[TXN-START] id={}, type={}, amount={}, src={}, dest={}, thread={}",
                txId,
                request != null ? request.getType() : null,
                request != null ? request.getAmount() : null,
                request != null ? request.getSourceAccount() : null,
                request != null ? request.getDestinationAccount() : null,
                threadName);

        try {
            // Step 2: Validate Request
            validationService.validateRequest(request);

            // Step 2.5: AI Pre-Commit Anomaly & Fraud Assessment
            FraudAssessmentResult fraudResult = fraudService.assessRisk(request);
            if (fraudResult.fraudFlagged()) {
                throw new BankingException(
                        "Transaction intercepted by AI Fraud Engine: " + String.join("; ", fraudResult.riskFactors()),
                        "ERR_FRAUD_DETECTED",
                        "SECURITY_FRAUD_RISK",
                        HttpStatus.FORBIDDEN
                );
            }

            // Step 3: Check Idempotency
            if (transactionRepository.existsById(request.getTransactionId())) {
                throw new DuplicateTransactionException(request.getTransactionId());
            }

            // Step 4 & 5: Acquire locks deterministically and execute database transaction
            try (AccountLockManager.OrderedLockPair ignored = lockManager.acquireDeterministicLocks(
                    request.getSourceAccount(), request.getDestinationAccount())) {

                TransactionResponse response = transactionTemplate.execute(status -> {
                    // Secondary database uniqueness check inside transaction
                    if (transactionRepository.existsById(request.getTransactionId())) {
                        throw new DuplicateTransactionException(request.getTransactionId());
                    }

                    return switch (request.getType()) {
                        case DEPOSIT -> executeDeposit(request, threadName, startTime);
                        case WITHDRAWAL -> executeWithdrawal(request, threadName, startTime);
                        case TRANSFER -> executeTransfer(request, threadName, startTime);
                    };
                });

                long duration = System.currentTimeMillis() - startTime;
                auditService.recordAudit(txId, request.getType().name(), threadName, duration, "SUCCESS");

                log.info("[TXN-SUCCESS] id={}, type={}, duration={}ms, thread={}",
                        txId, request.getType(), duration, threadName);
                return response;
            }

        } catch (BankingException be) {
            long duration = System.currentTimeMillis() - startTime;
            auditService.recordFailure(txId, be.getErrorCategory(), be.getErrorCode(), be.getMessage());
            auditService.recordAudit(txId, request != null && request.getType() != null ? request.getType().name() : "UNKNOWN",
                    threadName, duration, "FAILED");

            log.warn("[TXN-FAILED] id={}, code={}, category={}, reason={}, duration={}ms, thread={}",
                    txId, be.getErrorCode(), be.getErrorCategory(), be.getMessage(), duration, threadName);
            throw be;

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            auditService.recordFailure(txId, "SYSTEM_ERROR", "ERR_UNHANDLED_EXCEPTION", ex.getMessage());
            auditService.recordAudit(txId, request != null && request.getType() != null ? request.getType().name() : "UNKNOWN",
                    threadName, duration, "FAILED");

            log.error("[TXN-SYSTEM-ERROR] id={}, error={}, duration={}ms, thread={}",
                    txId, ex.getMessage(), duration, threadName, ex);
            throw ex;
        }
    }

    private TransactionResponse executeDeposit(TransactionRequest request, String threadName, long startTime) {
        Account dest = accountRepository.findWithLockByAccountId(request.getDestinationAccount())
                .orElseThrow(() -> new AccountNotFoundException(request.getDestinationAccount()));

        if (dest.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountInactiveException(dest.getAccountId(), dest.getStatus().name());
        }

        dest.setBalance(dest.getBalance().add(request.getAmount()));
        dest.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(dest);

        Transaction txn = new Transaction(
                request.getTransactionId(),
                null,
                dest,
                request.getAmount(),
                TransactionType.DEPOSIT,
                TransactionStatus.SUCCESS,
                LocalDateTime.now()
        );
        transactionRepository.save(txn);

        long duration = System.currentTimeMillis() - startTime;
        return new TransactionResponse(
                txn.getTransactionId(),
                null,
                dest.getAccountId(),
                txn.getAmount(),
                txn.getType(),
                txn.getStatus(),
                "Deposit successful",
                null,
                dest.getBalance(),
                threadName,
                duration,
                LocalDateTime.now()
        );
    }

    private TransactionResponse executeWithdrawal(TransactionRequest request, String threadName, long startTime) {
        Account src = accountRepository.findWithLockByAccountId(request.getSourceAccount())
                .orElseThrow(() -> new AccountNotFoundException(request.getSourceAccount()));

        if (src.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountInactiveException(src.getAccountId(), src.getStatus().name());
        }

        // Mandatory check: balance >= requested amount
        if (src.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    src.getAccountId(),
                    request.getAmount().toPlainString(),
                    src.getBalance().toPlainString()
            );
        }

        src.setBalance(src.getBalance().subtract(request.getAmount()));
        src.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(src);

        Transaction txn = new Transaction(
                request.getTransactionId(),
                src,
                null,
                request.getAmount(),
                TransactionType.WITHDRAWAL,
                TransactionStatus.SUCCESS,
                LocalDateTime.now()
        );
        transactionRepository.save(txn);

        long duration = System.currentTimeMillis() - startTime;
        return new TransactionResponse(
                txn.getTransactionId(),
                src.getAccountId(),
                null,
                txn.getAmount(),
                txn.getType(),
                txn.getStatus(),
                "Withdrawal successful",
                src.getBalance(),
                null,
                threadName,
                duration,
                LocalDateTime.now()
        );
    }

    private TransactionResponse executeTransfer(TransactionRequest request, String threadName, long startTime) {
        // Enforce deterministic row-level DB lock acquisition order (lower account ID first)
        String srcId = request.getSourceAccount();
        String destId = request.getDestinationAccount();

        Account firstLocked;
        Account secondLocked;

        if (srcId.compareTo(destId) < 0) {
            firstLocked = accountRepository.findWithLockByAccountId(srcId)
                    .orElseThrow(() -> new AccountNotFoundException(srcId));
            secondLocked = accountRepository.findWithLockByAccountId(destId)
                    .orElseThrow(() -> new AccountNotFoundException(destId));
        } else {
            secondLocked = accountRepository.findWithLockByAccountId(destId)
                    .orElseThrow(() -> new AccountNotFoundException(destId));
            firstLocked = accountRepository.findWithLockByAccountId(srcId)
                    .orElseThrow(() -> new AccountNotFoundException(srcId));
        }

        Account src = srcId.equals(firstLocked.getAccountId()) ? firstLocked : secondLocked;
        Account dest = destId.equals(firstLocked.getAccountId()) ? firstLocked : secondLocked;

        if (src.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountInactiveException(src.getAccountId(), src.getStatus().name());
        }
        if (dest.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountInactiveException(dest.getAccountId(), dest.getStatus().name());
        }

        if (src.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    src.getAccountId(),
                    request.getAmount().toPlainString(),
                    src.getBalance().toPlainString()
            );
        }

        src.setBalance(src.getBalance().subtract(request.getAmount()));
        dest.setBalance(dest.getBalance().add(request.getAmount()));

        src.setUpdatedAt(LocalDateTime.now());
        dest.setUpdatedAt(LocalDateTime.now());

        accountRepository.save(src);
        accountRepository.save(dest);

        Transaction txn = new Transaction(
                request.getTransactionId(),
                src,
                dest,
                request.getAmount(),
                TransactionType.TRANSFER,
                TransactionStatus.SUCCESS,
                LocalDateTime.now()
        );
        transactionRepository.save(txn);

        long duration = System.currentTimeMillis() - startTime;
        return new TransactionResponse(
                txn.getTransactionId(),
                src.getAccountId(),
                dest.getAccountId(),
                txn.getAmount(),
                txn.getType(),
                txn.getStatus(),
                "Transfer successful",
                src.getBalance(),
                dest.getBalance(),
                threadName,
                duration,
                LocalDateTime.now()
        );
    }
}
