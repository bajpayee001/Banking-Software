package com.bank.transaction.dto;

import java.math.BigDecimal;
import java.util.List;

public record ConcurrencyTestResult(
    String accountId,
    BigDecimal openingBalance,
    BigDecimal finalBalance,
    boolean invariantSatisfied,
    String summary,
    List<ThreadExecutionDetail> threadDetails
) {
    public record ThreadExecutionDetail(
        String threadName,
        String transactionId,
        BigDecimal requestedAmount,
        String status,
        String message,
        long durationMs
    ) {}
}
