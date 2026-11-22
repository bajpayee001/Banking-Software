package com.bank.transaction.exception;

import com.bank.transaction.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(org.springframework.security.authentication.BadCredentialsException ex) {
        log.warn("Authentication failure: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                null,
                "ERR_AUTHENTICATION_FAILED",
                "SECURITY_ERROR",
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                null,
                "ERR_ACCESS_DENIED",
                "SECURITY_ERROR",
                "Access denied: Insufficient privileges to perform this operation",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid input argument: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                null,
                "ERR_INVALID_ARGUMENT",
                "VALIDATION_ERROR",
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ErrorResponse> handleBankingException(BankingException ex) {
        log.warn("Banking business exception caught: code={}, category={}, message={}",
                ex.getErrorCode(), ex.getErrorCategory(), ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                null,
                ex.getErrorCode(),
                ex.getErrorCategory(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, ex.getHttpStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled server exception: ", ex);

        ErrorResponse response = new ErrorResponse(
                null,
                "ERR_INTERNAL_SERVER_ERROR",
                "SYSTEM_ERROR",
                ex.getMessage() != null ? ex.getMessage() : "An unexpected internal error occurred",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
