package com.smartfinance.shared.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends SmartFinanceException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
