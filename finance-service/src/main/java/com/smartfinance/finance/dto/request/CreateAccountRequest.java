package com.smartfinance.finance.dto.request;

import com.smartfinance.finance.entity.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull AccountType type,
        @NotBlank @Size(min = 3, max = 3) String currency,
        String color,
        String icon
) {}