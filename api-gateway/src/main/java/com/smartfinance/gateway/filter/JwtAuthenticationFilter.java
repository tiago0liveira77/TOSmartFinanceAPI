package com.smartfinance.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartfinance.shared.dto.ErrorResponse;
import com.smartfinance.shared.exception.UnauthorizedException;
import com.smartfinance.shared.security.JwtValidator;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Filtro global JWT do API Gateway. Corre antes de todos os outros filtros (Order -1).
 *
 * FLUXO POR REQUEST:
 *   1. OPTIONS (CORS preflight) → passa sempre sem validação
 *   2. Path em PUBLIC_PATHS → passa sem validação (ex: login, register)
 *   3. Outros paths → valida Bearer token:
 *      - Token válido → injeta X-User-Id e X-User-Email no request downstream
 *      - Token ausente/inválido → responde 401 imediatamente
 *
 * HEADERS INJETADOS DOWNSTREAM:
 *   X-User-Id    → UUID do utilizador autenticado (lido nos controllers via @RequestHeader)
 *   X-User-Email → email do utilizador (informativo, pode estar vazio)
 *   Authorization → mantido para que os serviços downstream possam validar independentemente
 *
 * NOTA: O header X-Internal-Service NÃO é passado ao exterior — apenas tráfego interno
 *   (dentro da rede Docker) deve usar este mecanismo de autenticação inter-serviço.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";

    /**
     * Paths que ignoram validação JWT. Suporta padrões Ant (**, *).
     * Atualizar esta lista quando novos endpoints públicos forem adicionados.
     */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/oauth2/**",
            "/actuator/**",
            "/actuator/health"
    );

    private final JwtValidator jwtValidator;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper;

    @Autowired
    public JwtAuthenticationFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        // CORS preflight — passa sempre sem validação
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }

        // Paths públicos — passa sem validação
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Extrai Bearer token do header Authorization
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("[GATEWAY] Request rejected — missing Authorization header: path={}", path);
            return rejectUnauthorized(exchange, "Token de autenticação ausente");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtValidator.validateToken(token);
            String userId = claims.getSubject();
            String email = claims.get("email", String.class);

            // Injeta headers de identidade no request downstream.
            // Os microserviços leem X-User-Id para identificar o utilizador,
            // sem precisar de re-validar o JWT individualmente.
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(HEADER_USER_ID, userId)
                    .header(HEADER_USER_EMAIL, email != null ? email : "")
                    // Mantém o Authorization para serviços que precisam de validar o JWT
                    // diretamente (ex: finance-service via JwtAuthenticationFilter próprio)
                    .build();

            log.debug("[GATEWAY] Request authenticated: userId={}, path={}", userId, path);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (UnauthorizedException e) {
            log.debug("[GATEWAY] JWT validation failed: path={}, reason={}", path, e.getMessage());
            return rejectUnauthorized(exchange, "Token inválido ou expirado");
        } catch (Exception e) {
            log.warn("[GATEWAY] Unexpected error during JWT validation: path={}, error={}", path, e.getMessage());
            return rejectUnauthorized(exchange, "Erro de autenticação");
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> rejectUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = ErrorResponse.of(message, HttpStatus.UNAUTHORIZED.value());
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            body = "{\"message\":\"Unauthorized\",\"status\":401}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
