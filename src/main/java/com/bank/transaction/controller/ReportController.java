package com.bank.transaction.controller;

import com.bank.transaction.dto.ReportDTO;
import com.bank.transaction.service.ReportingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    private final ReportingService reportingService;

    public ReportController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping
    public ResponseEntity<List<ReportDTO>> getAllReports() {
        return ResponseEntity.ok(reportingService.getAllReports());
    }

    @GetMapping("/1")
    public ResponseEntity<ReportDTO> getTop10Accounts() {
        return ResponseEntity.ok(reportingService.getTop10AccountsByVolume());
    }

    @GetMapping("/2")
    public ResponseEntity<ReportDTO> getFailedGroupedByReason() {
        return ResponseEntity.ok(reportingService.getFailedTransactionsGroupedByReason());
    }

    @GetMapping("/3")
    public ResponseEntity<ReportDTO> getDailyByType() {
        return ResponseEntity.ok(reportingService.getDailyTransactionAmountByType());
    }

    @GetMapping("/4")
    public ResponseEntity<ReportDTO> getAccountsWithMoreThanN(
            @RequestParam(defaultValue = "1") int minTransactions) {
        return ResponseEntity.ok(reportingService.getAccountsWithMoreThanNTransactions(minTransactions));
    }

    @GetMapping("/5")
    public ResponseEntity<ReportDTO> getCustomerHistory(
            @RequestParam(defaultValue = "CUST101") String customerId) {
        return ResponseEntity.ok(reportingService.getCustomerTransactionHistory(customerId));
    }
}
