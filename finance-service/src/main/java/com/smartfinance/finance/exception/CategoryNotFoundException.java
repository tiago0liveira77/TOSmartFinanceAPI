package com.smartfinance.finance.exception;

import com.smartfinance.shared.exception.ResourceNotFoundException;

import java.util.UUID;

public class CategoryNotFoundException extends ResourceNotFoundException {
    public CategoryNotFoundException(UUID id) {
        super("Category", id);
    }
}
