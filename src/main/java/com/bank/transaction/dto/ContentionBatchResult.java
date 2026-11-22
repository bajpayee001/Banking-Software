package com.bank.transaction.dto;

import java.math.BigDecimal;
import java.util.List;

public record ContentionBatchResult(
    String targetAccount,
    int totalRequestsInBatch,
    BigDecimal totalBatchedAmount,
    long standardProcessingTimeMs,
    long aiBatchedProcessingTimeMs,
    double speedupFactor,
    double standardTps,
    double aiBatchedTps,
    String architecturalBenefit,
    List<String> batchedTransactionIds
) {}
