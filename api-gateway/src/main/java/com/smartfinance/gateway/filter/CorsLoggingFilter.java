package com.smartfinance.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Filtra requests com header "Origin" e loga quando o origin não está na lista permitida.
 *
 * O Spring Cloud Gateway trata os requests CORS bloqueados com 403 antes de chegar
 * ao JwtAuthenticationFilter. Este WebFilter corre antes do processamento CORS e
 * deteta origens não permitidas para logging explícito.
 *
 * Ordem: Ordered.HIGHEST_PRECEDENCE → corre antes de todos os outros filtros.
 *
 * NOTA: A lista de origins é duplicada em cors-logging.allowed-origins porque
 * @Value não consegue resolver chaves YAML com [/**] (spring.cloud.gateway.globalcors...).
 */
@Component
@Slf4j
@EnableConfigurationProperties(CorsLoggingFilter.CorsLoggingProperties.class)
public class CorsLoggingFilter implements WebFilter, Ordered {

    @ConfigurationProperties(prefix = "cors-logging")
    public record CorsLoggingProperties(List<String> allowedOrigins) {}

    private final List<String> allowedOrigins;

    public CorsLoggingFilter(CorsLoggingProperties props) {
        this.allowedOrigins = props.allowedOrigins();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);

        if (origin == null) {
            return chain.filter(exchange);
        }

        String remoteIp = getRemoteIp(request);
        boolean allowed = allowedOrigins.stream().anyMatch(o -> o.equals(origin));

        if (!allowed) {
            log.warn("[GATEWAY][CORS] Blocked request: origin='{}', ip={}, method={}, path={}",
                    origin, remoteIp, request.getMethod(), request.getURI().getPath());
        } else {
            log.debug("[GATEWAY][CORS] Allowed request: origin='{}', ip={}, path={}",
                    origin, remoteIp, request.getURI().getPath());
        }

        return chain.filter(exchange).doOnSuccess(v -> {
            if (!allowed) {
                ServerHttpResponse response = exchange.getResponse();
                log.debug("[GATEWAY][CORS] Blocked response status: origin='{}', status={}",
                        origin, response.getStatusCode());
            }
        });
    }

    private String getRemoteIp(ServerHttpRequest request) {
        // Tenta X-Forwarded-For primeiro (quando o gateway está atrás de um proxy/load balancer)
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
