package com.bank.transaction.controller;

import com.bank.transaction.model.Account;
import com.bank.transaction.model.AccountStatus;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.CustomerRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AdminController(UserRepository userRepository,
                           CustomerRepository customerRepository,
                           AccountRepository accountRepository,
                           TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Admin-only: Returns high-level system summary metrics.
     */
    @GetMapping("/system-summary")
    public ResponseEntity<Map<String, Object>> getSystemSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalUsers", userRepository.count());
        summary.put("totalCustomers", customerRepository.count());
        summary.put("totalAccounts", accountRepository.count());
        summary.put("activeAccounts", accountRepository.countByStatus(AccountStatus.ACTIVE));
        summary.put("frozenAccounts", accountRepository.countByStatus(AccountStatus.FROZEN));
        summary.put("totalTransactions", transactionRepository.count());
        summary.put("systemStatus", "OPERATIONAL");
        return ResponseEntity.ok(summary);
    }

    /**
     * Admin-only: Freeze an account.
     */
    @PostMapping("/accounts/{accountId}/freeze")
    public ResponseEntity<Map<String, Object>> freezeAccount(@PathVariable String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        account.setStatus(AccountStatus.FROZEN);
        accountRepository.save(account);

        Map<String, Object> response = new HashMap<>();
        response.put("accountId", accountId);
        response.put("status", "FROZEN");
        response.put("message", "Account successfully frozen by administrator");
        return ResponseEntity.ok(response);
    }
}
