package com.smartfinance.shared.security;

import io.jsonwebtoken.Claims;

public interface JwtValidator {

    /**
     * Validates the given JWT token and returns its claims.
     *
     * @param token the raw JWT string (without "Bearer " prefix)
     * @return parsed and validated Claims
     * @throws com.smartfinance.shared.exception.UnauthorizedException if token is invalid or expired
     */
    Claims validateToken(String token);

    /**
     * Extracts the subject (userId) from a token without full validation.
     * Use only for non-security-critical contexts.
     */
    String extractSubject(String token);
}
