package com.smartfinance.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50) String icon,
        @Size(max = 7) String color
) {}
