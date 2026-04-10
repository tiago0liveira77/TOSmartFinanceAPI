package com.smartfinance.ai.service;

import org.springframework.stereotype.Component;

/**
 * Wrapper Spring do DescriptionNormalizer da shared-lib.
 * Permite injeção via @RequiredArgsConstructor nos services do ai-service.
 */
@Component
public class DescriptionNormalizer {

    public String normalize(String raw) {
        return com.smartfinance.shared.util.DescriptionNormalizer.normalize(raw);
    }
}
