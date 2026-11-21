package com.bank.transaction;

import com.bank.transaction.model.*;
import com.bank.transaction.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class Day2DataAccessTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionAuditRepository transactionAuditRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Checkpoint 2.1: Verify Pre-seeded Multi-channel Accounts and Balances")
    void testSeedDataLoadedSuccessfully() {
        // Verify customers
        List<Customer> customers = customerRepository.findAll();
        assertThat(customers).hasSizeGreaterThanOrEqualTo(5);

        // Verify mandatory concurrency test account (ACC1001 with exactly 10,000 INR)
        Optional<Account> acc1001 = accountRepository.findById("ACC1001");
        assertThat(acc1001).isPresent();
        assertThat(acc1001.get().getBalance()).isEqualByComparingTo("10000.00");
        assertThat(acc1001.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);

        // Verify frozen and closed edge case accounts
        Optional<Account> frozenAcc = accountRepository.findById("ACC3001");
        assertThat(frozenAcc).isPresent();
        assertThat(frozenAcc.get().getStatus()).isEqualTo(AccountStatus.FROZEN);

        Optional<Account> closedAcc = accountRepository.findById("ACC6001");
        assertThat(closedAcc).isPresent();
        assertThat(closedAcc.get().getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    @DisplayName("Checkpoint 2.2: Verify Pre-seeded Admin & User Accounts and Role Separation")
    void testUserAndAdminPreSeeded() {
        // Verify Admin user
        Optional<User> adminOpt = userRepository.findByUsername("admin");
        assertThat(adminOpt).isPresent();
        assertThat(adminOpt.get().getRole()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(adminOpt.get().getCustomer()).isNull(); // System admin has no individual customer account

        // Verify Regular User linked to Customer
        Optional<User> userOpt = userRepository.findByUsername("rahul_sharma");
        assertThat(userOpt).isPresent();
        assertThat(userOpt.get().getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(userOpt.get().getCustomer()).isNotNull();
        assertThat(userOpt.get().getCustomer().getCustomerId()).isEqualTo("CUST101");

        // Verify dynamically creating a new Admin
        User newAdmin = new User(
                "USER-ADMIN-02",
                "finance_officer",
                "officer@banktest.com",
                "hashedPass123",
                Role.ROLE_ADMIN,
                null,
                true,
                "SECRETKEY2FA",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        userRepository.save(newAdmin);

        Optional<User> savedAdmin = userRepository.findByUsername("finance_officer");
        assertThat(savedAdmin).isPresent();
        assertThat(savedAdmin.get().getRole()).isEqualTo(Role.ROLE_ADMIN);
    }

    @Test
    @DisplayName("Checkpoint 2.3: Verify Custom Repository Queries and Customer Transaction History")
    void testCustomerTransactionHistoryQueries() {
        // Query recent transactions by customer CUST101
        List<Transaction> cust101Txns = transactionRepository.findRecentByCustomer("CUST101", PageRequest.of(0, 10));
        assertThat(cust101Txns).isNotEmpty();

        // Query accounts by status
        List<Account> activeAccounts = accountRepository.findByStatus(AccountStatus.ACTIVE);
        assertThat(activeAccounts).isNotEmpty();
        long activeCount = accountRepository.countByStatus(AccountStatus.ACTIVE);
        assertThat(activeCount).isEqualTo(activeAccounts.size());

        // Verify baseline audits
        List<TransactionAudit> audits = transactionAuditRepository.findAll();
        assertThat(audits).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @Transactional
    @DisplayName("Checkpoint 2.4: Verify Atomic SQL Balance Debit and Credit Updates")
    void testAtomicBalanceDebitAndCredit() {
        // Initial balance of ACC1001 is 10,000.00
        BigDecimal withdrawAmount = new BigDecimal("3000.00");

        // 1. Successful atomic debit (sufficient balance)
        int updatedRows = accountRepository.debitAtomic("ACC1001", withdrawAmount);
        assertThat(updatedRows).isEqualTo(1);

        Account afterDebit = accountRepository.findById("ACC1001").orElseThrow();
        assertThat(afterDebit.getBalance()).isEqualByComparingTo("7000.00");

        // 2. Unsuccessful atomic debit (exceeding available balance)
        BigDecimal excessiveAmount = new BigDecimal("15000.00");
        int failedRows = accountRepository.debitAtomic("ACC1001", excessiveAmount);
        assertThat(failedRows).isEqualTo(0); // Protected: No rows updated!

        Account afterFailedDebit = accountRepository.findById("ACC1001").orElseThrow();
        assertThat(afterFailedDebit.getBalance()).isEqualByComparingTo("7000.00"); // Remains untouched!

        // 3. Successful atomic credit
        BigDecimal creditAmount = new BigDecimal("2500.00");
        int creditRows = accountRepository.creditAtomic("ACC1001", creditAmount);
        assertThat(creditRows).isEqualTo(1);

        Account afterCredit = accountRepository.findById("ACC1001").orElseThrow();
        assertThat(afterCredit.getBalance()).isEqualByComparingTo("9500.00");
    }
}
