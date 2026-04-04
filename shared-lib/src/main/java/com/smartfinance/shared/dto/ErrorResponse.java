package com.smartfinance.shared.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        String message,
        int status,
        LocalDateTime timestamp,
        Map<String, String> errors
) {
    public static ErrorResponse of(String message, int status) {
        return new ErrorResponse(message, status, LocalDateTime.now(), Map.of());
    }

    public static ErrorResponse of(String message, int status, Map<String, String> errors) {
        return new ErrorResponse(message, status, LocalDateTime.now(), errors);
    }
}
