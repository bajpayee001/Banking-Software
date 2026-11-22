package com.bank.transaction.dto;

import com.bank.transaction.model.AccountStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountDTO(
    String accountId,
    String customerId,
    String customerName,
    String accountNumber,
    BigDecimal balance,
    AccountStatus status,
    String currency,
    LocalDateTime updatedAt
) {}
