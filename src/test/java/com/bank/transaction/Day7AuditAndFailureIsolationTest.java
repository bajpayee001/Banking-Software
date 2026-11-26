package com.bank.transaction;

import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.exception.BankingException;
import com.bank.transaction.exception.InsufficientBalanceException;
import com.bank.transaction.model.*;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.FailedTransactionRepository;
import com.bank.transaction.repository.TransactionAuditRepository;
import com.bank.transaction.service.AuditService;
import com.bank.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class Day7AuditAndFailureIsolationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionAuditRepository auditRepository;

    @Autowired
    private FailedTransactionRepository failedRepository;

    @Autowired
    private com.bank.transaction.repository.TransactionRepository transactionRepository;

    @Test
    @DisplayName("Checkpoint 7.1: Failure Isolation & Audit Trail on Overdraft Attempt")
    void testFailureIsolationOnOverdraft() {
        String txId = "FAIL-701-" + UUID.randomUUID().toString().substring(0, 8);
        Account accountBefore = accountRepository.findById("ACC1001").orElseThrow();
        BigDecimal initialBalance = accountBefore.getBalance();

        // Attempt withdrawal exceeding balance (initial ₹10,000 -> request ₹15,000)
        TransactionRequest request = new TransactionRequest(
                txId,
                "ACC1001",
                null,
                new BigDecimal("15000.00"),
                TransactionType.WITHDRAWAL
        );

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(BankingException.class);

        // 1. Invariant: Account balance must be completely untouched
        Account accountAfter = accountRepository.findById("ACC1001").orElseThrow();
        assertThat(accountAfter.getBalance()).isEqualByComparingTo(initialBalance);

        // 2. Invariant: Failure must be recorded in FAILED_TRANSACTION via REQUIRES_NEW
        List<FailedTransaction> failures = failedRepository.findByTransactionId(txId);
        assertThat(failures).hasSize(1);
        FailedTransaction failure = failures.get(0);
        assertThat(failure.getErrorCode()).isEqualTo("ERR_INSUFFICIENT_FUNDS");
        assertThat(failure.getErrorCategory()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(failure.getFailureReason()).contains("insufficient balance");

        // 3. Invariant: Audit entry must be recorded with status FAILED
        List<TransactionAudit> audits = auditRepository.findByTransactionId(txId);
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(audits.get(0).getOperationType()).isEqualTo("WITHDRAWAL");
    }

    @Test
    @DisplayName("Checkpoint 7.2: Failure Telemetry on Frozen Account Access")
    void testFailureIsolationOnFrozenAccount() {
        String txId = "FAIL-702-" + UUID.randomUUID().toString().substring(0, 8);

        TransactionRequest request = new TransactionRequest(
                txId,
                "ACC3001", // ACC3001 is FROZEN
                null,
                new BigDecimal("500.00"),
                TransactionType.WITHDRAWAL
        );

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(BankingException.class);

        List<FailedTransaction> failures = failedRepository.findByTransactionId(txId);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).getErrorCode()).isEqualTo("ERR_ACCOUNT_INACTIVE");
        assertThat(failures.get(0).getErrorCategory()).isEqualTo("VALIDATION_FAILURE");
    }

    @Test
    @DisplayName("Checkpoint 7.3: Failure Isolation on AI Flagged Fraud Anomaly")
    void testFailureIsolationOnAiFraudDetection() {
        String txId = "FAIL-703-" + UUID.randomUUID().toString().substring(0, 8);

        // Pre-condition: Trigger velocity anomaly by seeding recent operations
        Account target = accountRepository.findById("ACC2001").orElseThrow();
        transactionRepository.save(new Transaction("TXN-V1-" + UUID.randomUUID().toString().substring(0, 6), null, target, new BigDecimal("100.00"), TransactionType.DEPOSIT, TransactionStatus.SUCCESS, java.time.LocalDateTime.now()));
        transactionRepository.save(new Transaction("TXN-V2-" + UUID.randomUUID().toString().substring(0, 6), null, target, new BigDecimal("100.00"), TransactionType.DEPOSIT, TransactionStatus.SUCCESS, java.time.LocalDateTime.now()));

        // Velocity burst + radical balance drain + high value spike triggers AI fraud engine (> 0.85 threshold)
        TransactionRequest request = new TransactionRequest(
                txId,
                "ACC2001",
                null,
                new BigDecimal("98000.00"),
                TransactionType.WITHDRAWAL
        );

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("AI Fraud Engine");

        List<FailedTransaction> failures = failedRepository.findByTransactionId(txId);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).getErrorCode()).isEqualTo("ERR_FRAUD_DETECTED");
        assertThat(failures.get(0).getErrorCategory()).isEqualTo("SECURITY_FRAUD_RISK");
    }

    @Test
    @DisplayName("Checkpoint 7.4: Direct AuditService Execution in REQUIRES_NEW Boundaries")
    void testDirectAuditServiceExecution() {
        String manualTxId = "MANUAL-704-" + UUID.randomUUID().toString().substring(0, 8);

        auditService.recordAudit(manualTxId, "DEPOSIT", "AuditWorker-1", 15, "SUCCESS");
        auditService.recordFailure(manualTxId, "CONNECTIVITY_ERROR", "ERR_DB_TIMEOUT", "Connection wait timed out");

        List<TransactionAudit> audits = auditRepository.findByTransactionId(manualTxId);
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getStatus()).isEqualTo("SUCCESS");

        List<FailedTransaction> failures = failedRepository.findByTransactionId(manualTxId);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).getErrorCode()).isEqualTo("ERR_DB_TIMEOUT");
    }

    @Test
    @DisplayName("Checkpoint 7.5: Successful Transactions Leave Audit Without Failure Entries")
    void testSuccessfulTransactionAuditCleanliness() {
        String txId = "SUCC-705-" + UUID.randomUUID().toString().substring(0, 8);

        TransactionRequest request = new TransactionRequest(
                txId,
                null,
                "ACC1002",
                new BigDecimal("1000.00"),
                TransactionType.DEPOSIT
        );

        TransactionResponse response = transactionService.processTransaction(request);
        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);

        // Audit entry exists and is SUCCESS
        List<TransactionAudit> audits = auditRepository.findByTransactionId(txId);
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getStatus()).isEqualTo("SUCCESS");

        // FAILED_TRANSACTION must have ZERO entries for this successful transaction
        List<FailedTransaction> failures = failedRepository.findByTransactionId(txId);
        assertThat(failures).isEmpty();
    }
}
