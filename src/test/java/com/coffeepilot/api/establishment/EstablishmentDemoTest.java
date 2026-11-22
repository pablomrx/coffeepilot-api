package com.coffeepilot.api.establishment;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstablishmentDemoTest {

    private static final Instant START = Instant.parse("2026-11-21T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(START, ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofMinutes(45);

    private static OffsetDateTime now() {
        return OffsetDateTime.now(CLOCK);
    }

    @Test
    void demoRejectsNullClock() {
        assertThrows(IllegalArgumentException.class,
                () -> Establishment.demo("Demo", TTL, null));
    }

    @Test
    void demoRejectsNullTtl() {
        assertThrows(IllegalArgumentException.class,
                () -> Establishment.demo("Demo", null, CLOCK));
    }

    @Test
    void demoRejectsZeroTtl() {
        assertThrows(IllegalArgumentException.class,
                () -> Establishment.demo("Demo", Duration.ZERO, CLOCK));
    }

    @Test
    void demoRejectsNegativeTtl() {
        assertThrows(IllegalArgumentException.class,
                () -> Establishment.demo("Demo", Duration.ofMinutes(-1), CLOCK));
    }

    @Test
    void demoCalculatesExpirationFromClockAndTtl() {
        Establishment demo = Establishment.demo("Demo", TTL, CLOCK);

        assertTrue(demo.isDemo());
        assertEquals(now().plus(TTL), demo.getDemoExpiresAt());
    }

    @Test
    void demoTruncatesExpirationToMicroseconds() {
        Clock withNanos = Clock.fixed(
                Instant.parse("2026-11-21T12:00:00.123456789Z"),
                ZoneOffset.UTC
        );

        Establishment demo = Establishment.demo("Demo", TTL, withNanos);

        assertEquals(123456000, demo.getDemoExpiresAt().getNano());
    }

    @Test
    void demoIsNotExpiredBeforeExpiration() {
        Establishment demo = Establishment.demo("Demo", TTL, CLOCK);

        assertFalse(demo.isDemoExpired(now().plus(TTL).minusNanos(1000)));
    }

    @Test
    void demoIsExpiredExactlyAtExpiration() {
        Establishment demo = Establishment.demo("Demo", TTL, CLOCK);

        assertTrue(demo.isDemoExpired(now().plus(TTL)));
    }

    @Test
    void demoIsExpiredAfterExpiration() {
        Establishment demo = Establishment.demo("Demo", TTL, CLOCK);

        assertTrue(demo.isDemoExpired(now().plus(TTL).plusSeconds(1)));
    }

    @Test
    void realEstablishmentIsNotDemoAndNeverExpires() {
        Establishment real = new Establishment("Cafeteria");

        assertFalse(real.isDemo());
        assertNull(real.getDemoExpiresAt());
        assertFalse(real.isDemoExpired(now().plusYears(100)));
    }

    @Test
    void isUsableWhenActiveAndWithinValidity() {
        Establishment demo = Establishment.demo("Demo", TTL, CLOCK);

        assertTrue(demo.isUsable(now()));
    }

    @Test
    void isNotUsableWhenDemoExpired() {
        Establishment demo = Establishment.demo("Demo", TTL, CLOCK);

        assertFalse(demo.isUsable(now().plus(TTL)));
    }

    @Test
    void isNotUsableWhenInactive() {
        Establishment real = new Establishment("Cafeteria");
        real.setActive(false);

        assertFalse(real.isUsable(now()));
    }
}
