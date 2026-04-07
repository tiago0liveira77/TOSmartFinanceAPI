package com.smartfinance.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String message,
        String conversationId
) {}
