package com.smartfinance.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        @NotBlank @Size(max = 255) String name,
        String color,
        String icon,
        Boolean isActive
) {}