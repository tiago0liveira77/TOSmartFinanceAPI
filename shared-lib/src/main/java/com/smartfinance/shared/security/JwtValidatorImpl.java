package com.smartfinance.shared.security;

import com.smartfinance.shared.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Stub implementation — logic will be added in Sprint 1 (auth-service).
 * Shared across api-gateway, finance-service, ai-service, notification-service
 * so they can validate JWT tokens issued by auth-service.
 */
public class JwtValidatorImpl implements JwtValidator {

    private final SecretKey secretKey;

    public JwtValidatorImpl(String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid or expired JWT token");
        }
    }

    @Override
    public String extractSubject(String token) {
        return validateToken(token).getSubject();
    }
}
