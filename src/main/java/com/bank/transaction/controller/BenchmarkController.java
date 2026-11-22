package com.bank.transaction.controller;

import com.bank.transaction.dto.BenchmarkResult;
import com.bank.transaction.service.BenchmarkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/benchmark")
@CrossOrigin(origins = "*")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    /**
     * Executes the comparison test across 1, 5, 10, and 20 worker threads.
     * Can run for 1,000 (quick live test), 10,000 or the full 50,000 transactions.
     */
    @PostMapping("/run")
    public ResponseEntity<BenchmarkResult> runBenchmark(
            @RequestParam(defaultValue = "1000") int transactions) {
        // Guard boundary: min 100, max 50000
        int boundedCount = Math.max(100, Math.min(transactions, 50000));
        BenchmarkResult result = benchmarkService.runBenchmark(boundedCount);
        return ResponseEntity.ok(result);
    }
}
