package com.coffeepilot.api.mail;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.mail")
public record EmailQuotaProperties(

        @DefaultValue("250")
        @Min(1) @Max(300)
        int dailyLimit,

        @DefaultValue("50")
        @Min(0)
        int reservedForCritical
) {

    public EmailQuotaProperties {
        if (reservedForCritical >= dailyLimit) {
            throw new IllegalArgumentException(
                    "app.mail.reserved-for-critical deve ser menor que "
                            + "app.mail.daily-limit."
            );
        }
    }

    public int standardLimit() {
        return dailyLimit - reservedForCritical;
    }

    public int criticalLimit() {
        return dailyLimit;
    }
}
