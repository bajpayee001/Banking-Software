package com.bank.transaction.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatbotQueryResponse(
    String answer,
    String intent,
    List<String> suggestedQuestions,
    LocalDateTime timestamp
) {}
