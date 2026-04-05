package com.smartfinance.finance.dto.response;

import com.smartfinance.finance.entity.Account;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID userId,
        String name,
        String type,
        BigDecimal balance,
        String currency,
        String color,
        String icon,
        boolean isActive
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getUserId(), a.getName(),
                a.getType().name(), a.getBalance(), a.getCurrency(),
                a.getColor(), a.getIcon(), a.isActive());
    }
}