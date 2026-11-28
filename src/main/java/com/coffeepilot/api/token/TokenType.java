package com.coffeepilot.api.token;

import java.time.Duration;

public enum TokenType {

    EMAIL_CONFIRMATION(Duration.ofHours(24)),
    PASSWORD_RESET(Duration.ofMinutes(30)),
    EMPLOYEE_INVITE(Duration.ofHours(72));

    private final Duration validity;

    TokenType(Duration validity) {
        this.validity = validity;
    }

    public Duration validity() {
        return validity;
    }
}
