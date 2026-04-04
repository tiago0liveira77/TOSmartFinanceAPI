package com.smartfinance.shared.exception;

import org.springframework.http.HttpStatus;

public class SmartFinanceException extends RuntimeException {

    private final HttpStatus status;

    public SmartFinanceException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public SmartFinanceException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
