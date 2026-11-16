package com.coffeepilot.api.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RateLimitCleanup {

    private final RateLimitPolicy policy;

    public RateLimitCleanup(RateLimitPolicy policy) {
        this.policy = policy;
    }

    @Scheduled(fixedDelayString = "${rate-limit.cleanup-interval-ms:60000}")
    public void evictExpired() {
        policy.evictExpired();
    }
}
