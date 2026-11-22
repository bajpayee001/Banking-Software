package com.bank.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record FraudAssessmentResult(
    String transactionId,
    String accountId,
    BigDecimal amount,
    double riskScore, // 0.00 to 1.00
    boolean fraudFlagged,
    String riskLevel, // LOW, MEDIUM, CRITICAL
    List<String> riskFactors,
    LocalDateTime assessedAt
) {}
