package com.smartfinance.auth.service;

import com.smartfinance.auth.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final JwtConfig jwtConfig;

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtConfig.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gera um access token JWT assinado com HMAC-SHA.
     * Claims incluídos: sub (userId), email, iat, exp.
     * Duração: JwtConfig.accessTokenExpiration() segundos (padrão: 15 min = 900s).
     * NUNCA incluir password, refresh token ou dados sensíveis nos claims.
     */
    public String generateAccessToken(UUID userId, String email) {
        long now = System.currentTimeMillis();
        long expiration = jwtConfig.accessTokenExpiration();
        log.debug("[AUTH][JWT] Access token generated: userId={}, expiresIn={}s", userId, expiration);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiration * 1000))
                .signWith(secretKey())
                .compact();
    }

    /**
     * Valida a assinatura e expiração do token.
     * Lança JwtException (ou subclasse) se o token for inválido, expirado ou adulterado.
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
