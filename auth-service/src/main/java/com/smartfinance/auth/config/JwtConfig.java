package com.smartfinance.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtConfig(
        String secret,
        long accessTokenExpiration,   // segundos (900 = 15min)
        long refreshTokenExpiration   // segundos (604800 = 7 dias)
) {}
