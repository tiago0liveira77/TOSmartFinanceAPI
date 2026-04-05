package com.smartfinance.finance.exception;

import com.smartfinance.shared.exception.ResourceNotFoundException;

import java.util.UUID;

public class TransactionNotFoundException extends ResourceNotFoundException {
    public TransactionNotFoundException(UUID id) {
        super("Transaction", id);
    }
}