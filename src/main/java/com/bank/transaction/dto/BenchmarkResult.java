package com.bank.transaction.dto;

import java.util.List;

public record BenchmarkResult(
    int totalTransactionsRequested,
    List<ThreadBenchmarkMetric> threadMetrics,
    String bottleneckAnalysis
) {
    public record ThreadBenchmarkMetric(
        int workerThreads,
        int totalTransactions,
        int successfulTransactions,
        int failedTransactions,
        long totalTimeMs,
        double tps, // Transactions Per Second
        String observedBottleneck
    ) {}
}
