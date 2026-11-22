package com.bank.transaction.dto;

import java.time.LocalDateTime;

public record CustomerDTO(
    String customerId,
    String firstName,
    String lastName,
    String email,
    String phone,
    LocalDateTime createdAt
) {}
