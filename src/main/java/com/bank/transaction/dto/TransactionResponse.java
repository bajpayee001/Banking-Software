package com.bank.transaction.dto;

import com.bank.transaction.model.TransactionStatus;
import com.bank.transaction.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
    String transactionId,
    String sourceAccount,
    String destinationAccount,
    BigDecimal amount,
    TransactionType type,
    TransactionStatus status,
    String message,
    BigDecimal remainingSourceBalance,
    BigDecimal remainingDestinationBalance,
    String threadName,
    long durationMs,
    LocalDateTime timestamp
) {}
