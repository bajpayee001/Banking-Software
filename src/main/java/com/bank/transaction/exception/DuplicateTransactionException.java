package com.bank.transaction.exception;

import org.springframework.http.HttpStatus;

public class DuplicateTransactionException extends BankingException {

    public DuplicateTransactionException(String transactionId) {
        super("Transaction with ID '" + transactionId + "' has already been processed (Idempotency violation).",
              "ERR_DUPLICATE_TXN",
              "IDEMPOTENCY_VIOLATION",
              HttpStatus.CONFLICT);
    }
}
