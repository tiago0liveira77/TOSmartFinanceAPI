package com.smartfinance.auth.service;

import com.smartfinance.auth.config.JwtConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redis;
    private final JwtConfig jwtConfig;

    private static final String KEY_PREFIX = "rt:";

    // Cria um refresh token e guarda no Redis
    // Key: "rt:{userId}:{jti}"  Value: userId
    public String create(UUID userId) {
        String jti = UUID.randomUUID().toString();
        String key = KEY_PREFIX + userId + ":" + jti;
        redis.opsForValue().set(key, userId.toString(),
                Duration.ofSeconds(jwtConfig.refreshTokenExpiration()));
        return jti;  // devolvemos o jti como refresh token (opaco)
    }

    // Valida e invalida (rotação): apaga o token atual e cria um novo
    public Optional<String> rotateToken(UUID userId, String jti) {
        String key = KEY_PREFIX + userId + ":" + jti;
        Boolean exists = redis.hasKey(key);
        if (Boolean.FALSE.equals(exists)) return Optional.empty();
        redis.delete(key);
        return Optional.of(create(userId));
    }

    public void invalidateAll(UUID userId) {
        // apaga todos os refresh tokens do utilizador (logout)
        redis.delete(redis.keys(KEY_PREFIX + userId + ":*"));
    }
}