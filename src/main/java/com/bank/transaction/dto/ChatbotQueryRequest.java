package com.bank.transaction.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatbotQueryRequest(
    String query,
    String customerId,
    String accountId
) {}
