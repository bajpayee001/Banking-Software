package com.bank.transaction;

import com.bank.transaction.model.*;
import com.bank.transaction.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class Day1FoundationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionAuditRepository transactionAuditRepository;

    @Autowired
    private FailedTransactionRepository failedTransactionRepository;

    @Test
    @DisplayName("Checkpoint 1.1: Verify Customer & Account Persistence and Relationships")
    void testCustomerAndAccountPersistence() {
        Customer customer = new Customer("CUST-D1", "Vikram", "Aditya", "vikram.aditya@banktest.com", "+91-9988776655", LocalDateTime.now());
        customerRepository.save(customer);

        Account account = new Account(
                "ACC-D1",
                customer,
                "SB-99990001",
                new BigDecimal("50000.00"),
                AccountStatus.ACTIVE,
                "INR",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        accountRepository.save(account);

        Optional<Account> retrieved = accountRepository.findById("ACC-D1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getCustomer().getEmail()).isEqualTo("vikram.aditya@banktest.com");
        assertThat(retrieved.get().getBalance()).isEqualByComparingTo("50000.00");
        assertThat(retrieved.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Checkpoint 1.2: Verify Database Negative Balance Check Constraint")
    void testNegativeBalanceConstraintViolation() {
        Customer customer = new Customer("CUST-D2", "Ananya", "Sen", "ananya.sen@banktest.com", "+91-9988776656", LocalDateTime.now());
        customerRepository.save(customer);

        Account invalidAccount = new Account(
                "ACC-D2-INV",
                customer,
                "SB-99990002",
                new BigDecimal("-500.00"),
                AccountStatus.ACTIVE,
                "INR",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        assertThrows(DataIntegrityViolationException.class, () -> {
            accountRepository.saveAndFlush(invalidAccount);
        });
    }

    @Test
    @DisplayName("Checkpoint 1.3: Verify Unique Constraints and Idempotency Key Detection")
    void testUniqueConstraintsAndIdempotencyKey() {
        Customer customer = new Customer("CUST-D3", "Rohan", "Mehra", "rohan.mehra@banktest.com", "+91-9988776657", LocalDateTime.now());
        customerRepository.save(customer);

        Account account = new Account("ACC-D3", customer, "SB-99990003", new BigDecimal("10000.00"), AccountStatus.ACTIVE, "INR", LocalDateTime.now(), LocalDateTime.now());
        accountRepository.save(account);

        Transaction txn1 = new Transaction(
                "TXN-D1-001",
                null,
                account,
                new BigDecimal("1000.00"),
                TransactionType.DEPOSIT,
                TransactionStatus.SUCCESS,
                LocalDateTime.now()
        );
        transactionRepository.saveAndFlush(txn1);

        // Application-level idempotency detection
        boolean exists = transactionRepository.existsById("TXN-D1-001");
        assertThat(exists).isTrue();

        // Database-level uniqueness violation: Duplicate account number on another account
        Account duplicateAccountNumberAccount = new Account(
                "ACC-D3-DUP",
                customer,
                "SB-99990003", // Duplicate account number!
                new BigDecimal("20000.00"),
                AccountStatus.ACTIVE,
                "INR",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        assertThrows(DataIntegrityViolationException.class, () -> {
            accountRepository.saveAndFlush(duplicateAccountNumberAccount);
        });
    }

    @Test
    @DisplayName("Checkpoint 1.4: Verify Audit and Failed Transaction Entities")
    void testAuditAndFailedTransactionPersistence() {
        TransactionAudit audit = new TransactionAudit(
                "TXN-D1-001",
                "DEPOSIT",
                "TestWorkerThread-1",
                15L,
                "SUCCESS",
                LocalDateTime.now()
        );
        TransactionAudit savedAudit = transactionAuditRepository.save(audit);
        assertThat(savedAudit.getAuditId()).isNotNull();

        FailedTransaction failure = new FailedTransaction(
                "TXN-D1-FAIL-001",
                "BUSINESS_RULE_VIOLATION",
                "INSUFFICIENT_FUNDS",
                "Requested withdrawal amount exceeds available balance.",
                LocalDateTime.now()
        );
        FailedTransaction savedFailure = failedTransactionRepository.save(failure);
        assertThat(savedFailure.getFailureId()).isNotNull();
        assertThat(savedFailure.getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }
}
