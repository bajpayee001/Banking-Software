package com.bank.transaction.service;

import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.exception.InvalidTransactionException;
import com.bank.transaction.model.TransactionType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ValidationService {

    public void validateRequest(TransactionRequest request) {
        if (request == null) {
            throw new InvalidTransactionException("Transaction request body cannot be null");
        }

        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
            throw new InvalidTransactionException("Transaction ID is mandatory and must not be blank");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Transaction amount must be strictly greater than zero");
        }

        if (request.getType() == null) {
            throw new InvalidTransactionException("Transaction type is mandatory (DEPOSIT, WITHDRAWAL, TRANSFER)");
        }

        switch (request.getType()) {
            case DEPOSIT -> {
                if (request.getDestinationAccount() == null || request.getDestinationAccount().trim().isEmpty()) {
                    throw new InvalidTransactionException("Destination account is required for DEPOSIT");
                }
            }
            case WITHDRAWAL -> {
                if (request.getSourceAccount() == null || request.getSourceAccount().trim().isEmpty()) {
                    throw new InvalidTransactionException("Source account is required for WITHDRAWAL");
                }
            }
            case TRANSFER -> {
                if (request.getSourceAccount() == null || request.getSourceAccount().trim().isEmpty()) {
                    throw new InvalidTransactionException("Source account is required for TRANSFER");
                }
                if (request.getDestinationAccount() == null || request.getDestinationAccount().trim().isEmpty()) {
                    throw new InvalidTransactionException("Destination account is required for TRANSFER");
                }
                if (request.getSourceAccount().trim().equalsIgnoreCase(request.getDestinationAccount().trim())) {
                    throw new InvalidTransactionException("Source and Destination accounts cannot be the same for TRANSFER");
                }
            }
        }
    }
}
