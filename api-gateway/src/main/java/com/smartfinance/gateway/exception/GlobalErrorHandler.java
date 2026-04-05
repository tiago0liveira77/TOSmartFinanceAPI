package com.smartfinance.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartfinance.shared.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Global error handler for the API Gateway (WebFlux).
 *
 * <p>Intercepts all unhandled exceptions and returns a consistent JSON error body.
 * Ordered at -2 so it runs before Spring Boot's default {@code DefaultErrorWebExceptionHandler}.
 */
@Order(-2)
@Component
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    private final ObjectMapper objectMapper;

    public GlobalErrorHandler() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = resolveStatus(ex);
        String message = resolveMessage(ex, status);

        if (status.is5xxServerError()) {
            log.error("Gateway error [{}] on {}: {}", status.value(),
                    exchange.getRequest().getPath(), ex.getMessage());
        } else {
            log.debug("Gateway client error [{}] on {}: {}", status.value(),
                    exchange.getRequest().getPath(), ex.getMessage());
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = ErrorResponse.of(message, status.value());
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            body = fallbackBody(status.value());
        }

        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            return HttpStatus.resolve(rse.getStatusCode().value()) != null
                    ? HttpStatus.resolve(rse.getStatusCode().value())
                    : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (ex instanceof ConnectException || ex.getCause() instanceof ConnectException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (ex instanceof TimeoutException || ex.getCause() instanceof TimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveMessage(Throwable ex, HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Recurso não encontrado";
            case UNAUTHORIZED -> "Não autorizado";
            case FORBIDDEN -> "Acesso proibido";
            case TOO_MANY_REQUESTS -> "Demasiados pedidos. Tenta mais tarde.";
            case SERVICE_UNAVAILABLE -> "Serviço temporariamente indisponível";
            case GATEWAY_TIMEOUT -> "Timeout a aguardar resposta do serviço";
            default -> status.is5xxServerError()
                    ? "Erro interno do servidor"
                    : ex.getMessage();
        };
    }

    private byte[] fallbackBody(int status) {
        return ("{\"message\":\"Erro interno\",\"status\":" + status + "}").getBytes(StandardCharsets.UTF_8);
    }
}
