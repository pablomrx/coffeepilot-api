package com.coffeepilot.api.mail;

import com.coffeepilot.api.common.exception.EmailQuotaExceededException;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class EmailQuotaService {

    private final EmailUsageRepository usageRepository;
    private final EmailQuotaProperties properties;
    private final Clock clock;

    public EmailQuotaService(
            EmailUsageRepository usageRepository,
            EmailQuotaProperties properties,
            Clock clock
    ) {
        this.usageRepository = usageRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public void checkCapacity(EmailPriority priority) {
        Instant now = clock.instant();

        if (usageRepository.reservedOn(dayOf(now)) >= limitFor(priority)) {
            throw quotaExceeded(now);
        }
    }

    public boolean tryReserve(EmailPriority priority) {
        return usageRepository.tryIncrement(
                dayOf(clock.instant()),
                limitFor(priority)
        );
    }

    public void reserveOrThrow(EmailPriority priority) {
        Instant now = clock.instant();

        if (!usageRepository.tryIncrement(dayOf(now), limitFor(priority))) {
            throw quotaExceeded(now);
        }
    }

    private int limitFor(EmailPriority priority) {
        return switch (priority) {
            case CRITICAL -> properties.criticalLimit();
            case STANDARD -> properties.standardLimit();
        };
    }

    private static LocalDate dayOf(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }

    private static EmailQuotaExceededException quotaExceeded(Instant now) {
        Instant nextMidnight = dayOf(now)
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        Duration remaining = Duration.between(now, nextMidnight);
        long seconds = remaining.getSeconds()
                + (remaining.getNano() > 0 ? 1 : 0);

        return new EmailQuotaExceededException(Math.max(1, seconds));
    }
}
