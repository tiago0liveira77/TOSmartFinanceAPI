package com.smartfinance.auth.dto.response;

public record AuthResponse(
        String accessToken,
        long expiresIn,       // segundos
        UserResponse user
) {}