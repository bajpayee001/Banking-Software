package com.bank.transaction.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AiQueryOptimizationReport(
    int analyzedQueriesCount,
    List<QueryDiagnosis> diagnoses,
    String overallHealthRating, // OPTIMAL, GOOD, ATTENTION_REQUIRED
    LocalDateTime generatedAt
) {
    public record QueryDiagnosis(
        String queryTitle,
        String sqlExcerpt,
        String scanTypeDetected, // INDEX_SCAN, FULL_TABLE_SCAN, HASH_JOIN
        double estimatedScanCost,
        String aiDiagnosis,
        String recommendedAction
    ) {}
}
