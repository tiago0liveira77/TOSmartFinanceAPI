package com.smartfinance.finance.dto.response;

import com.smartfinance.finance.entity.Category;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String type,
        String icon,
        String color,
        UUID parentId,
        boolean isSystem
) {
    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getType().name(),
                c.getIcon(), c.getColor(),
                c.getParent() != null ? c.getParent().getId() : null,
                c.isSystem());
    }
}