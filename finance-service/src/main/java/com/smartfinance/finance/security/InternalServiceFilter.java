package com.smartfinance.finance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Permite chamadas inter-serviço sem JWT.
 * O ai-service envia o header "X-Internal-Service: ai-service" para identificar
 * que é uma chamada interna de serviço — não requer token do utilizador.
 */
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
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken("service:ai-service", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }
}
