package com.bank.transaction.dto;

import java.time.LocalDateTime;

public record ErrorResponse(
    String transactionId,
    String errorCode,
    String errorCategory,
    String message,
    LocalDateTime timestamp
) {}
