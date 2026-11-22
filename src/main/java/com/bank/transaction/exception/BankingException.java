package com.bank.transaction.exception;

import org.springframework.http.HttpStatus;

public class BankingException extends RuntimeException {

    private final String errorCode;
    private final String errorCategory;
    private final HttpStatus httpStatus;

    public BankingException(String message, String errorCode, String errorCategory, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.errorCategory = errorCategory;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() { return errorCode; }
    public String getErrorCategory() { return errorCategory; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}
