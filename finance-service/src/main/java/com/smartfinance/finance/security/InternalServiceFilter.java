package com.smartfinance.finance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro de segurança que autentica chamadas inter-serviço sem JWT.
 *
 * MODELO DE CONFIANÇA:
 *   Qualquer request com header "X-Internal-Service: ai-service" é tratado
 *   como autenticado com o principal "service:ai-service".
 *   O userId real é extraído do header "X-User-Id" (enviado explicitamente pelo ai-service).
 *   Os controllers acedem ao userId via SecurityContextHolder, tal como fazem com JWT.
 *
 * SEGURANÇA:
 *   - Este mecanismo assume que a rede interna é segura (ex: Docker bridge network).
 *   - O api-gateway NUNCA deve fazer pass-through do header X-Internal-Service ao exterior.
 *   - Sem esta garantia, um cliente externo poderia impersonar o ai-service e aceder
 *     a dados de qualquer utilizador passando um X-User-Id arbitrário.
 *   - Em produção, considerar mutual TLS ou tokens de serviço em vez de header simples.
 *
 * ORDEM NO CHAIN (ver SecurityConfig):
 *   Registado ANTES do JwtAuthenticationFilter.
 *   Se este filtro definir autenticação, o JWT filter não a sobrepõe
 *   (apenas define auth se o SecurityContext ainda estiver vazio).
 */
@Slf4j
public class InternalServiceFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Service";
    private static final String AI_SERVICE = "ai-service";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String serviceHeader = request.getHeader(HEADER);
        if (AI_SERVICE.equals(serviceHeader) &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            // Usa o userId do header X-User-Id como principal para compatibilidade
            // com getUserId() nos services (que lê o principal do SecurityContext)
            String userId = request.getHeader("X-User-Id");
            String principal = userId != null ? userId : "service:ai-service";

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("[FINANCE][SECURITY] Internal service access granted: service={}, userId={}, path={}",
                    serviceHeader, userId, request.getRequestURI());
        }

        chain.doFilter(request, response);
    }
}
