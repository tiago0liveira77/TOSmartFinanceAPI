package com.smartfinance.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record AiProperties(
        String apiKey,
        String baseUrl,
        String model,
        String modelFast,
        int maxTokens,
        double temperature
) {}
