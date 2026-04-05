package com.smartfinance.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartfinance.shared.dto.ErrorResponse;
import com.smartfinance.shared.exception.UnauthorizedException;
import com.smartfinance.shared.security.JwtValidator;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Global JWT authentication filter for the API Gateway.
 *
 * <p>Runs before all other filters (Order -1). For each incoming request:
 * <ul>
 *   <li>If the path is in the public whitelist → passes through unchanged.</li>
 *   <li>Otherwise → validates the Bearer token, adds X-User-Id and X-User-Email
 *       headers to the downstream request, or returns 401 if invalid/absent.</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";

    /**
     * Paths that bypass JWT validation entirely.
     * Supports Ant-style patterns (**, *).
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

        // Always allow OPTIONS (CORS preflight)
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }

        // Allow public paths without authentication
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Extract Bearer token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return rejectUnauthorized(exchange, "Token de autenticação ausente");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtValidator.validateToken(token);
            String userId = claims.getSubject();
            String email = claims.get("email", String.class);

            // Mutate request: add user identity headers for downstream services
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(HEADER_USER_ID, userId)
                    .header(HEADER_USER_EMAIL, email != null ? email : "")
                    // Remove Authorization from downstream if not needed
                    // (keep it so services can also validate independently if they choose to)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (UnauthorizedException e) {
            log.debug("JWT validation failed for path {}: {}", path, e.getMessage());
            return rejectUnauthorized(exchange, "Token inválido ou expirado");
        } catch (Exception e) {
            log.warn("Unexpected error during JWT validation for path {}: {}", path, e.getMessage());
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
