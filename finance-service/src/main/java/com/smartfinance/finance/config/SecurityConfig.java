package com.smartfinance.finance.config;

import com.smartfinance.finance.security.InternalServiceFilter;
import com.smartfinance.finance.security.JwtAuthenticationFilter;
import com.smartfinance.shared.security.JwtValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtValidator jwtValidator;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                // ORDEM DOS FILTROS DE SEGURANÇA:
                //   1. InternalServiceFilter — autentica chamadas de serviços internos via X-Internal-Service
                //   2. JwtAuthenticationFilter — autentica utilizadores externos via Bearer JWT
                // Esta ordem garante que chamadas internas são autenticadas sem tentar validar JWT.
                // Se ambos os headers estiverem presentes (situação anómala), o InternalServiceFilter
                // tem precedência porque define auth no SecurityContext antes do JWT filter correr.
                .addFilterBefore(new InternalServiceFilter(),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtValidator),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}