package com.bank.transaction;

import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.exception.AccountInactiveException;
import com.bank.transaction.exception.AccountNotFoundException;
import com.bank.transaction.exception.InsufficientBalanceException;
import com.bank.transaction.exception.InvalidTransactionException;
import com.bank.transaction.model.Account;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionStatus;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.TransactionService;
import com.bank.transaction.service.ValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class Day5CoreBankingTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("Checkpoint 5.1: Validation Gate Enforces Request Integrity")
    void testValidationGateRules() {
        // Null request
        assertThatThrownBy(() -> validationService.validateRequest(null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("cannot be null");

        // Missing transactionId
        TransactionRequest noTxId = new TransactionRequest("", null, "ACC1001", new BigDecimal("100.00"), TransactionType.DEPOSIT);
        assertThatThrownBy(() -> validationService.validateRequest(noTxId))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("Transaction ID is mandatory");

        // Zero / negative amount
        TransactionRequest zeroAmount = new TransactionRequest(UUID.randomUUID().toString(), null, "ACC1001", BigDecimal.ZERO, TransactionType.DEPOSIT);
        assertThatThrownBy(() -> validationService.validateRequest(zeroAmount))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("strictly greater than zero");

        // Missing destination account for DEPOSIT
        TransactionRequest noDest = new TransactionRequest(UUID.randomUUID().toString(), null, "", new BigDecimal("100.00"), TransactionType.DEPOSIT);
        assertThatThrownBy(() -> validationService.validateRequest(noDest))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("Destination account is required");

        // Missing source account for WITHDRAWAL
        TransactionRequest noSrc = new TransactionRequest(UUID.randomUUID().toString(), "", null, new BigDecimal("100.00"), TransactionType.WITHDRAWAL);
        assertThatThrownBy(() -> validationService.validateRequest(noSrc))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("Source account is required");
    }

    @Test
    @DisplayName("Checkpoint 5.2: Atomic Deposit Operation Crediting Account")
    void testAtomicDeposit() {
        String txId = "DEP-501-" + UUID.randomUUID().toString().substring(0, 8);
        Account accountBefore = accountRepository.findById("ACC1002").orElseThrow();
        BigDecimal initialBalance = accountBefore.getBalance();
        BigDecimal depositAmount = new BigDecimal("2500.00");

        TransactionRequest request = new TransactionRequest(
                txId,
                null,
                "ACC1002",
                depositAmount,
                TransactionType.DEPOSIT
        );

        TransactionResponse response = transactionService.processTransaction(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.remainingDestinationBalance()).isEqualByComparingTo(initialBalance.add(depositAmount));

        // Verify database persistence
        Account accountAfter = accountRepository.findById("ACC1002").orElseThrow();
        assertThat(accountAfter.getBalance()).isEqualByComparingTo(initialBalance.add(depositAmount));

        Transaction persistedTxn = transactionRepository.findById(txId).orElseThrow();
        assertThat(persistedTxn.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(persistedTxn.getAmount()).isEqualByComparingTo(depositAmount);
        assertThat(persistedTxn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    @DisplayName("Checkpoint 5.3: Atomic Withdrawal Operation Debiting Account")
    void testAtomicWithdrawal() {
        String txId = "WTH-502-" + UUID.randomUUID().toString().substring(0, 8);
        Account accountBefore = accountRepository.findById("ACC1002").orElseThrow();
        BigDecimal initialBalance = accountBefore.getBalance();
        BigDecimal withdrawAmount = new BigDecimal("1500.00");

        TransactionRequest request = new TransactionRequest(
                txId,
                "ACC1002",
                null,
                withdrawAmount,
                TransactionType.WITHDRAWAL
        );

        TransactionResponse response = transactionService.processTransaction(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.remainingSourceBalance()).isEqualByComparingTo(initialBalance.subtract(withdrawAmount));

        // Verify database persistence
        Account accountAfter = accountRepository.findById("ACC1002").orElseThrow();
        assertThat(accountAfter.getBalance()).isEqualByComparingTo(initialBalance.subtract(withdrawAmount));

        Transaction persistedTxn = transactionRepository.findById(txId).orElseThrow();
        assertThat(persistedTxn.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(persistedTxn.getAmount()).isEqualByComparingTo(withdrawAmount);
    }

    @Test
    @DisplayName("Checkpoint 5.4: Overdraft Rejection (Negative Balance Prevention)")
    void testOverdraftRejection() {
        String txId = "OVD-503-" + UUID.randomUUID().toString().substring(0, 8);
        Account account = accountRepository.findById("ACC1002").orElseThrow();
        BigDecimal initialBalance = account.getBalance();
        // Request amount exceeding balance
        BigDecimal overdraftAmount = initialBalance.add(new BigDecimal("1000.00"));

        TransactionRequest request = new TransactionRequest(
                txId,
                "ACC1002",
                null,
                overdraftAmount,
                TransactionType.WITHDRAWAL
        );

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("insufficient balance");

        // Verify balance was untouched
        Account accountAfter = accountRepository.findById("ACC1002").orElseThrow();
        assertThat(accountAfter.getBalance()).isEqualByComparingTo(initialBalance);
    }

    @Test
    @DisplayName("Checkpoint 5.5: Frozen/Inactive Account Rejection")
    void testInactiveAccountRejection() {
        String txId = "INA-504-" + UUID.randomUUID().toString().substring(0, 8);
        // ACC3001 is seeded with FROZEN status
        TransactionRequest request = new TransactionRequest(
                txId,
                "ACC3001",
                null,
                new BigDecimal("500.00"),
                TransactionType.WITHDRAWAL
        );

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessageContaining("is not active");
    }

    @Test
    @DisplayName("Checkpoint 5.6: Non-Existent Account Rejection")
    void testAccountNotFoundRejection() {
        String txId = "NF-505-" + UUID.randomUUID().toString().substring(0, 8);
        TransactionRequest request = new TransactionRequest(
                txId,
                "ACC9999_NON_EXISTENT",
                null,
                new BigDecimal("500.00"),
                TransactionType.WITHDRAWAL
        );

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("Account not found");
    }
}
