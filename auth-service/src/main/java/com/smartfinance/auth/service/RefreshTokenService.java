package com.smartfinance.auth.service;

import com.smartfinance.auth.config.JwtConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Gere os refresh tokens usando Redis como store efémero (chave: "rt:{jti}").
 *
 * ESTRATÉGIA DE ROTAÇÃO (rotate):
 *   Cada uso de um refresh token invalida o token atual e emite um novo.
 *   Objetivo: detetar reutilização de tokens roubados — se o mesmo jti for usado
 *   duas vezes, o segundo uso falha porque o token já foi apagado na primeira rotação.
 *   Isto permite detetar furto de sessão: se um atacante usar o token roubado antes
 *   do utilizador legítimo, o próximo uso legítimo irá falhar (token não encontrado).
 *
 * LIMITAÇÃO CONHECIDA: existe uma race condition teórica se dois requests chegarem
 *   simultaneamente com o mesmo jti — ambos podem ler o token antes de um o invalidar.
 *   Aceitável para a fase atual; em produção, considerar WATCH/MULTI/EXEC do Redis
 *   (transação otimista) para garantir atomicidade.
 *
 * FORMATO DAS CHAVES: "rt:{jti}" → "{userId}"
 * TTL: definido em JwtConfig.refreshTokenExpiration() (padrão: 7 dias = 604800s).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final StringRedisTemplate redis;
    private final JwtConfig jwtConfig;

    // Prefixo das chaves Redis para distinguir de outras chaves no mesmo namespace
    private static final String KEY_PREFIX = "rt:";

    /**
     * Cria um refresh token opaco (jti = UUID aleatório) e persiste no Redis com TTL.
     * O jti é devolvido ao cliente como cookie HttpOnly — nunca expor no body JSON.
     */
    public String create(UUID userId) {
        String jti = UUID.randomUUID().toString();
        long ttl = jwtConfig.refreshTokenExpiration();
        redis.opsForValue().set(KEY_PREFIX + jti, userId.toString(), Duration.ofSeconds(ttl));
        log.debug("[AUTH][REDIS] Refresh token created: userId={}, jti={}, ttl={}s", userId, jti, ttl);
        return jti;
    }

    /**
     * Valida o jti e devolve o userId associado sem apagar o token.
     * Usado apenas para leitura — a rotação real é feita em rotate().
     */
    public Optional<UUID> getUserId(String jti) {
        String value = redis.opsForValue().get(KEY_PREFIX + jti);
        return Optional.ofNullable(value).map(UUID::fromString);
    }

    /**
     * Rotação: invalida o jti atual e emite um novo token para o mesmo utilizador.
     * Devolve o novo jti, ou empty se o token não existe (expirado, já usado, ou inválido).
     */
    public Optional<String> rotate(String jti) {
        String key = KEY_PREFIX + jti;
        String userIdStr = redis.opsForValue().get(key);
        if (userIdStr == null) {
            log.warn("[AUTH][REDIS] Refresh token rotate failed — token not found (expired or reused): jti={}", jti);
            return Optional.empty();
        }
        redis.delete(key);
        String newJti = create(UUID.fromString(userIdStr));
        log.info("[AUTH][REDIS] Refresh token rotated: oldJti={}, newJti={}", jti, newJti);
        return Optional.of(newJti);
    }

    /**
     * Invalida um refresh token específico (chamado no logout).
     * Após este método, o token deixa de ser válido mesmo que não tenha expirado.
     */
    public void invalidate(String jti) {
        redis.delete(KEY_PREFIX + jti);
        log.info("[AUTH][REDIS] Refresh token invalidated (logout): jti={}", jti);
    }
}
