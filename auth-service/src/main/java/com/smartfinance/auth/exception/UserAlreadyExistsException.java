package com.smartfinance.auth.exception;

import com.smartfinance.shared.exception.SmartFinanceException;
import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends SmartFinanceException {
    public UserAlreadyExistsException(String email) {
        super("User already exists with email: " + email, HttpStatus.CONFLICT);
    }
}