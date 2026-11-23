package com.coffeepilot.api.demo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.demo")
public record DemoProperties(

        @DefaultValue("false")
        boolean enabled,

        @DefaultValue("10")
        @Min(0) @Max(100)
        int maxActiveSandboxes,

        @DefaultValue("45")
        @Min(1) @Max(240)
        int ttlMinutes
) {

    public Duration ttl() {
        return Duration.ofMinutes(ttlMinutes);
    }
}
