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

    // Key: "rt:{jti}"  Value: userId
    private static final String KEY_PREFIX = "rt:";

    /** Cria um refresh token opaco (jti) e guarda no Redis. */
    public String create(UUID userId) {
        String jti = UUID.randomUUID().toString();
        redis.opsForValue().set(
                KEY_PREFIX + jti,
                userId.toString(),
                Duration.ofSeconds(jwtConfig.refreshTokenExpiration()));
        return jti;
    }

    /** Valida o jti e devolve o userId associado (sem o apagar). */
    public Optional<UUID> getUserId(String jti) {
        String value = redis.opsForValue().get(KEY_PREFIX + jti);
        return Optional.ofNullable(value).map(UUID::fromString);
    }

    /**
     * Rotação: invalida o jti atual e emite um novo.
     * Devolve o novo jti, ou empty se o token não existe.
     */
    public Optional<String> rotate(String jti) {
        String key = KEY_PREFIX + jti;
        String userIdStr = redis.opsForValue().get(key);
        if (userIdStr == null) return Optional.empty();
        redis.delete(key);
        return Optional.of(create(UUID.fromString(userIdStr)));
    }

    /** Invalida um refresh token específico (logout). */
    public void invalidate(String jti) {
        redis.delete(KEY_PREFIX + jti);
    }
}
