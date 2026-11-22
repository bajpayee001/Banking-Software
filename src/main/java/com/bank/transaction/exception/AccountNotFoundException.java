package com.bank.transaction.exception;

import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends BankingException {

    public AccountNotFoundException(String accountId) {
        super("Account not found with identifier: " + accountId,
              "ERR_ACCOUNT_NOT_FOUND",
              "VALIDATION_FAILURE",
              HttpStatus.NOT_FOUND);
    }
}
