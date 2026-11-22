package com.bank.transaction.exception;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends BankingException {

    public InsufficientBalanceException(String accountId, String requestedAmount, String availableBalance) {
        super("Account " + accountId + " has insufficient balance: requested " + requestedAmount + ", available " + availableBalance,
              "ERR_INSUFFICIENT_FUNDS",
              "BUSINESS_RULE_VIOLATION",
              HttpStatus.BAD_REQUEST);
    }
}
