package com.bank.transaction.controller;

import com.bank.transaction.dto.AccountDTO;
import com.bank.transaction.model.Account;
import com.bank.transaction.repository.AccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public ResponseEntity<List<AccountDTO>> getAllAccounts() {
        List<AccountDTO> list = accountRepository.findAll().stream()
                .map(this::mapToDTO)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDTO> getAccountById(@PathVariable String accountId) {
        return accountRepository.findById(accountId)
                .map(this::mapToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{accountId}/reset-10k")
    public ResponseEntity<AccountDTO> resetAccountToTenThousand(@PathVariable String accountId) {
        return accountRepository.findById(accountId)
                .map(acc -> {
                    acc.setBalance(new BigDecimal("10000.00"));
                    acc.setUpdatedAt(LocalDateTime.now());
                    accountRepository.save(acc);
                    return ResponseEntity.ok(mapToDTO(acc));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private AccountDTO mapToDTO(Account acc) {
        String custName = acc.getCustomer() != null
                ? acc.getCustomer().getFirstName() + " " + acc.getCustomer().getLastName()
                : "Unknown";
        String custId = acc.getCustomer() != null ? acc.getCustomer().getCustomerId() : null;

        return new AccountDTO(
                acc.getAccountId(),
                custId,
                custName,
                acc.getAccountNumber(),
                acc.getBalance(),
                acc.getStatus(),
                acc.getCurrency(),
                acc.getUpdatedAt()
        );
    }
}
