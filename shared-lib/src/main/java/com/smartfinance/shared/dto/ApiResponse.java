package com.smartfinance.shared.dto;

public record ApiResponse<T>(
        T data,
        String message,
        int status
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, "OK", 200);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(data, "Created", 201);
    }

    public static <T> ApiResponse<T> of(T data, String message, int status) {
        return new ApiResponse<>(data, message, status);
    }
}
