package com.smartfinance.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /** RestTemplate para chamar o finance-service */
    @Bean
    @org.springframework.context.annotation.Primary
    public RestTemplate financeRestTemplate() {
        return new RestTemplate();
    }

    /** RestTemplate separado para chamar a AI API (Groq / OpenAI-compatible) */
    @Bean("aiRestTemplate")
    public RestTemplate aiRestTemplate() {
        return new RestTemplate();
    }
}
