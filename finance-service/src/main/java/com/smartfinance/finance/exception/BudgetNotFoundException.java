package com.smartfinance.finance.exception;

import com.smartfinance.shared.exception.ResourceNotFoundException;

import java.util.UUID;

public class BudgetNotFoundException extends ResourceNotFoundException {
    public BudgetNotFoundException(UUID id) {
        super("Budget", id);
    }
}