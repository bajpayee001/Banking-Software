package com.bank.transaction.controller;

import com.bank.transaction.dto.ConcurrencyTestResult;
import com.bank.transaction.service.ConcurrencyEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/concurrency")
@CrossOrigin(origins = "*")
public class ConcurrencyController {

    private final ConcurrencyEngine concurrencyEngine;

    public ConcurrencyController(ConcurrencyEngine concurrencyEngine) {
        this.concurrencyEngine = concurrencyEngine;
    }

    @PostMapping("/run-mandatory-scenario")
    public ResponseEntity<ConcurrencyTestResult> runMandatoryScenario() throws InterruptedException {
        ConcurrencyTestResult result = concurrencyEngine.runMandatoryConcurrencyScenario();
        return ResponseEntity.ok(result);
    }
}
