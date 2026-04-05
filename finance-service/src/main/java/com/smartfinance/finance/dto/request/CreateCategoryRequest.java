package com.smartfinance.finance.dto.request;

import com.smartfinance.finance.entity.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull CategoryType type,
        @Size(max = 50) String icon,
        @Size(max = 7) String color,
        UUID parentId
) {}