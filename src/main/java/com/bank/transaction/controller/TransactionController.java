package com.bank.transaction.controller;

import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.model.FailedTransaction;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionAudit;
import com.bank.transaction.repository.FailedTransactionRepository;
import com.bank.transaction.repository.TransactionAuditRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "*")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final TransactionAuditRepository auditRepository;
    private final FailedTransactionRepository failedRepository;

    public TransactionController(TransactionService transactionService,
                                 TransactionRepository transactionRepository,
                                 TransactionAuditRepository auditRepository,
                                 FailedTransactionRepository failedRepository) {
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
        this.failedRepository = failedRepository;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> executeTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.processTransaction(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> getRecentTransactions(@RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(transactionRepository.findTopRecent(PageRequest.of(0, limit)));
    }

    @GetMapping("/audit")
    public ResponseEntity<List<TransactionAudit>> getRecentAudits(@RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(auditRepository.findRecent(PageRequest.of(0, limit)));
    }

    @GetMapping("/failed")
    public ResponseEntity<List<FailedTransaction>> getRecentFailed(@RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(failedRepository.findRecent(PageRequest.of(0, limit)));
    }
}
