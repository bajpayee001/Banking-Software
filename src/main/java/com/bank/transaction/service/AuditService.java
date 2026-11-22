package com.bank.transaction.service;

import com.bank.transaction.model.FailedTransaction;
import com.bank.transaction.model.TransactionAudit;
import com.bank.transaction.repository.FailedTransactionRepository;
import com.bank.transaction.repository.TransactionAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final TransactionAuditRepository auditRepository;
    private final FailedTransactionRepository failedTransactionRepository;

    public AuditService(TransactionAuditRepository auditRepository,
                        FailedTransactionRepository failedTransactionRepository) {
        this.auditRepository = auditRepository;
        this.failedTransactionRepository = failedTransactionRepository;
    }

    /**
     * Persists audit log in an isolated independent transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(String transactionId, String operationType, String threadName,
                            long durationMs, String status) {
        try {
            TransactionAudit audit = new TransactionAudit(
                    transactionId,
                    operationType,
                    threadName,
                    durationMs,
                    status,
                    LocalDateTime.now()
            );
            auditRepository.save(audit);
            log.info("[AUDIT] txId={}, op={}, thread={}, duration={}ms, status={}",
                    transactionId, operationType, threadName, durationMs, status);
        } catch (Exception ex) {
            log.error("Failed to write transaction audit for txId={}: {}", transactionId, ex.getMessage());
        }
    }

    /**
     * Persists failed transaction record in an isolated independent transaction
     * so that rollback of monetary balances does NOT lose failure telemetry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String transactionId, String errorCategory, String errorCode,
                              String reason) {
        try {
            FailedTransaction failure = new FailedTransaction(
                    transactionId,
                    errorCategory,
                    errorCode,
                    reason,
                    LocalDateTime.now()
            );
            failedTransactionRepository.save(failure);
            log.warn("[FAILURE-RECORDED] txId={}, category={}, code={}, reason={}",
                    transactionId, errorCategory, errorCode, reason);
        } catch (Exception ex) {
            log.error("Failed to write failed_transaction record for txId={}: {}", transactionId, ex.getMessage());
        }
    }
}
