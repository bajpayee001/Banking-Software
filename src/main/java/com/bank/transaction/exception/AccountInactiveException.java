package com.bank.transaction.exception;

import org.springframework.http.HttpStatus;

public class AccountInactiveException extends BankingException {

    public AccountInactiveException(String accountId, String status) {
        super("Account " + accountId + " is not active. Current status: " + status,
              "ERR_ACCOUNT_INACTIVE",
              "VALIDATION_FAILURE",
              HttpStatus.BAD_REQUEST);
    }
}
