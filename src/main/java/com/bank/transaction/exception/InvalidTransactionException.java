package com.bank.transaction.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransactionException extends BankingException {

    public InvalidTransactionException(String message) {
        super(message,
              "ERR_INVALID_TRANSACTION",
              "VALIDATION_FAILURE",
              HttpStatus.BAD_REQUEST);
    }
}
