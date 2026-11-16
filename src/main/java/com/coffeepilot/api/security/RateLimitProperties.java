package com.coffeepilot.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        @DefaultValue("5") int loginPerMinute,
        @DefaultValue("3") int registerPerHour,
        @DefaultValue("120") int generalPerMinute,
        @DefaultValue("100000") int maxKeys,
        List<Rule> rules
) {

    public RateLimitProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public record Rule(String method, String path, int max, Duration window) {
    }
}
