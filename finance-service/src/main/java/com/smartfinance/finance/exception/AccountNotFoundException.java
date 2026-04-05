package com.smartfinance.finance.exception;

import com.smartfinance.shared.exception.ResourceNotFoundException;

import java.util.UUID;

public class AccountNotFoundException extends ResourceNotFoundException {
    public AccountNotFoundException(UUID id) {
        super("Account", id);
    }
}