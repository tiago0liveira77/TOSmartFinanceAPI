package com.smartfinance.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate limiting configuration using Redis token bucket algorithm.
 *
 * <p>Provides two beans required by Spring Cloud Gateway's RequestRateLimiter filter:
 * <ul>
 *   <li>{@code defaultRateLimiter} — 20 requests/sec sustained, burst up to 40</li>
 *   <li>{@code ipKeyResolver} — uses client IP as the rate limit key</li>
 * </ul>
 */
@Configuration
public class RateLimitConfig {

    /**
     * Redis-backed rate limiter.
     *
     * <p>replenishRate=20: tokens added per second (sustained throughput)
     * burstCapacity=40:   max tokens at any instant (allows short spikes)
     * requestedTokens=1:  each request costs 1 token
     */
    @Bean
    public RedisRateLimiter defaultRateLimiter() {
        return new RedisRateLimiter(20, 40, 1);
    }

    /**
     * Key resolver based on client IP address.
     *
     * <p>Falls back to "anonymous" if the remote address cannot be determined
     * (e.g., behind a proxy without X-Forwarded-For). For production, consider
     * using X-Forwarded-For or the authenticated userId for per-user limiting.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            var remoteAddress = exchange.getRequest().getRemoteAddress();
            String key = (remoteAddress != null)
                    ? remoteAddress.getAddress().getHostAddress()
                    : "anonymous";
            return Mono.just(key);
        };
    }
}
