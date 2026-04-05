package com.smartfinance.gateway.config;

import com.smartfinance.shared.security.JwtValidator;
import com.smartfinance.shared.security.JwtValidatorImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for the API Gateway.
 *
 * <p>Spring Security is intentionally minimal here — JWT validation is handled
 * by {@link com.smartfinance.gateway.filter.JwtAuthenticationFilter} as a
 * GlobalFilter. Spring Security is kept to ensure CSRF is disabled and sessions
 * remain stateless.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public JwtValidator jwtValidator() {
        return new JwtValidatorImpl(jwtSecret);
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // All access control is handled by JwtAuthenticationFilter (GlobalFilter)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }
}
