package com.smartfinance.auth.exception;

import com.smartfinance.shared.exception.SmartFinanceException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends SmartFinanceException {
    public InvalidCredentialsException() {
        super("Invalid email or password", HttpStatus.UNAUTHORIZED);
    }
}