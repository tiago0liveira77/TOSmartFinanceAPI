package com.smartfinance.finance.config;

import com.smartfinance.shared.security.JwtValidator;
import com.smartfinance.shared.security.JwtValidatorImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class JwtValidatorConfig {

    private final JwtConfig jwtConfig;

    @Bean
    public JwtValidator jwtValidator() {
        return new JwtValidatorImpl(jwtConfig.secret());
    }
}