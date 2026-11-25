package com.bank.transaction;

import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.exception.AccountInactiveException;
import com.bank.transaction.exception.AccountNotFoundException;
import com.bank.transaction.exception.InsufficientBalanceException;
import com.bank.transaction.exception.InvalidTransactionException;
import com.bank.transaction.model.Account;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionAudit;
import com.bank.transaction.model.TransactionStatus;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.TransactionAuditRepository;
import com.bank.transaction.repository.TransactionRepository;
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
public class Day6TransferServiceTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionAuditRepository auditRepository;

    @Test
    @DisplayName("Checkpoint 6.1: Atomic Account-to-Account Transfer Success")
    void testSuccessfulTransfer() {
        String txId = "TRF-601-" + UUID.randomUUID().toString().substring(0, 8);
        Account srcBefore = accountRepository.findById("ACC1001").orElseThrow();
        Account destBefore = accountRepository.findById("ACC2004").orElseThrow();

        BigDecimal srcInitial = srcBefore.getBalance();
        BigDecimal destInitial = destBefore.getBalance();
        BigDecimal transferAmount = new BigDecimal("2000.00");

        TransactionRequest request = new TransactionRequest(
                txId,
                "ACC1001",
                "ACC2004",
                transferAmount,
                TransactionType.TRANSFER
        );

        TransactionResponse response = transactionService.processTransaction(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.remainingSourceBalance()).isEqualByComparingTo(srcInitial.subtract(transferAmount));
        assertThat(response.remainingDestinationBalance()).isEqualByComparingTo(destInitial.add(transferAmount));

        // Verify database state for source and destination accounts
        Account srcAfter = accountRepository.findById("ACC1001").orElseThrow();
        Account destAfter = accountRepository.findById("ACC2004").orElseThrow();

        assertThat(srcAfter.getBalance()).isEqualByComparingTo(srcInitial.subtract(transferAmount));
        assertThat(destAfter.getBalance()).isEqualByComparingTo(destInitial.add(transferAmount));

        // Verify transaction record
        Transaction txn = transactionRepository.findById(txId).orElseThrow();
        assertThat(txn.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(txn.getAmount()).isEqualByComparingTo(transferAmount);
        assertThat(txn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(txn.getSourceAccount().getAccountId()).isEqualTo("ACC1001");
        assertThat(txn.getDestinationAccount().getAccountId()).isEqualTo("ACC2004");
    }

    @Test
    @DisplayName("Checkpoint 6.2: ACID Rollback on Insufficient Source Balance")
    void testRollbackOnInsufficientBalance() {
        String txId = "TRF-602-" + UUID.randomUUID().toString().substring(0, 8);
        Account srcBefore = accountRepository.findById("ACC1001").orElseThrow();
        Account destBefore = accountRepository.findById("ACC2004").orElseThrow();

        BigDecimal srcInitial = srcBefore.getBalance();
        BigDecimal destInitial = destBefore.getBalance();
        BigDecimal excessiveAmount = srcInitial.add(new BigDecimal("5000.00"));

        TransactionRequest request = new TransactionRequest(
                txId,
                "ACC1001",
                "ACC2004",
                excessiveAmount,
                TransactionType.TRANSFER
        );

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("insufficient balance");

        // Verify ACID rollback: neither account balance was modified
        Account srcAfter = accountRepository.findById("ACC1001").orElseThrow();
        Account destAfter = accountRepository.findById("ACC2004").orElseThrow();

        assertThat(srcAfter.getBalance()).isEqualByComparingTo(srcInitial);
        assertThat(destAfter.getBalance()).isEqualByComparingTo(destInitial);
    }

    @Test
    @DisplayName("Checkpoint 6.3: ACID Rollback on Inactive Destination Account")
    void testRollbackOnInactiveDestination() {
        String txId = "TRF-603-" + UUID.randomUUID().toString().substring(0, 8);
        Account srcBefore = accountRepository.findById("ACC1001").orElseThrow();
        Account destBefore = accountRepository.findById("ACC3001").orElseThrow(); // ACC3001 is FROZEN

        BigDecimal srcInitial = srcBefore.getBalance();
        BigDecimal destInitial = destBefore.getBalance();
        BigDecimal transferAmount = new BigDecimal("1000.00");

        TransactionRequest request = new TransactionRequest(
                txId,
                "ACC1001",
                "ACC3001",
                transferAmount,
                TransactionType.TRANSFER
        );

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessageContaining("is not active");

        // Verify source balance was NOT debited
        Account srcAfter = accountRepository.findById("ACC1001").orElseThrow();
        Account destAfter = accountRepository.findById("ACC3001").orElseThrow();

        assertThat(srcAfter.getBalance()).isEqualByComparingTo(srcInitial);
        assertThat(destAfter.getBalance()).isEqualByComparingTo(destInitial);
    }

    @Test
    @DisplayName("Checkpoint 6.4: ACID Rollback on Non-Existent Destination Account")
    void testRollbackOnNonExistentDestination() {
        String txId = "TRF-604-" + UUID.randomUUID().toString().substring(0, 8);
        Account srcBefore = accountRepository.findById("ACC1001").orElseThrow();
        BigDecimal srcInitial = srcBefore.getBalance();

        TransactionRequest request = new TransactionRequest(
                txId,
                "ACC1001",
                "ACC9999_NON_EXISTENT",
                new BigDecimal("500.00"),
                TransactionType.TRANSFER
        );

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("Account not found");

        Account srcAfter = accountRepository.findById("ACC1001").orElseThrow();
        assertThat(srcAfter.getBalance()).isEqualByComparingTo(srcInitial);
    }

    @Test
    @DisplayName("Checkpoint 6.5: Validation Failure on Self-Transfer (Same Account)")
    void testValidationFailureOnSelfTransfer() {
        String txId = "TRF-605-" + UUID.randomUUID().toString().substring(0, 8);
        TransactionRequest request = new TransactionRequest(
                txId,
                "ACC1001",
                "ACC1001",
                new BigDecimal("500.00"),
                TransactionType.TRANSFER
        );

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("Source and Destination accounts cannot be the same");
    }

    @Test
    @DisplayName("Checkpoint 6.6: Transfer Audit Trail Durability")
    void testTransferAuditTrail() {
        String txId = "TRF-606-" + UUID.randomUUID().toString().substring(0, 8);
        TransactionRequest request = new TransactionRequest(
                txId,
                "ACC1001",
                "ACC2004",
                new BigDecimal("750.00"),
                TransactionType.TRANSFER
        );

        transactionService.processTransaction(request);

        List<TransactionAudit> audits = auditRepository.findByTransactionId(txId);
        assertThat(audits).isNotEmpty();
        TransactionAudit audit = audits.get(0);
        assertThat(audit.getOperationType()).isEqualTo("TRANSFER");
        assertThat(audit.getStatus()).isEqualTo("SUCCESS");
        assertThat(audit.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(audit.getThreadName()).isNotBlank();
    }
}
