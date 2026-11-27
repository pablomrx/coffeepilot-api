package com.coffeepilot.api.mail;

import com.coffeepilot.api.common.exception.EmailQuotaExceededException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailQuotaServiceTest {

    private static final Instant NOON = Instant.parse("2026-11-25T12:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 25);
    private static final EmailQuotaProperties PROPERTIES =
            new EmailQuotaProperties(250, 50);

    @Mock
    private EmailUsageRepository repository;

    private EmailQuotaService serviceAt(Clock clock) {
        return new EmailQuotaService(repository, PROPERTIES, clock);
    }

    private EmailQuotaService serviceAt(Instant instant) {
        return serviceAt(Clock.fixed(instant, ZoneOffset.UTC));
    }

    // ---------- checkCapacity ----------

    @Test
    void checkCapacityPassesBelowTheStandardLimit() {
        when(repository.reservedOn(DAY)).thenReturn(199);

        assertDoesNotThrow(
                () -> serviceAt(NOON).checkCapacity(EmailPriority.STANDARD));
    }

    @Test
    void checkCapacityFailsForStandardAtTheStandardLimit() {
        when(repository.reservedOn(DAY)).thenReturn(200);

        assertThrows(EmailQuotaExceededException.class,
                () -> serviceAt(NOON).checkCapacity(EmailPriority.STANDARD));
    }

    @Test
    void criticalStillHasCapacityWhereStandardIsExhausted() {
        when(repository.reservedOn(DAY)).thenReturn(200);

        assertDoesNotThrow(
                () -> serviceAt(NOON).checkCapacity(EmailPriority.CRITICAL));
    }

    @Test
    void checkCapacityPassesForCriticalJustBelowTheTotal() {
        when(repository.reservedOn(DAY)).thenReturn(249);

        assertDoesNotThrow(
                () -> serviceAt(NOON).checkCapacity(EmailPriority.CRITICAL));
    }

    @Test
    void checkCapacityFailsForCriticalAtTheTotal() {
        when(repository.reservedOn(DAY)).thenReturn(250);

        assertThrows(EmailQuotaExceededException.class,
                () -> serviceAt(NOON).checkCapacity(EmailPriority.CRITICAL));
    }

    // ---------- tryReserve ----------

    @Test
    void tryReserveUsesTheStandardLimitForStandard() {
        when(repository.tryIncrement(DAY, 200)).thenReturn(true);

        assertTrue(serviceAt(NOON).tryReserve(EmailPriority.STANDARD));
        verify(repository).tryIncrement(DAY, 200);
    }

    @Test
    void tryReserveUsesTheTotalForCritical() {
        when(repository.tryIncrement(DAY, 250)).thenReturn(true);

        assertTrue(serviceAt(NOON).tryReserve(EmailPriority.CRITICAL));
        verify(repository).tryIncrement(DAY, 250);
    }

    @Test
    void tryReserveReturnsFalseWhenTheRepositoryRefuses() {
        when(repository.tryIncrement(DAY, 200)).thenReturn(false);

        assertFalse(serviceAt(NOON).tryReserve(EmailPriority.STANDARD));
    }

    // ---------- reserveOrThrow ----------

    @Test
    void reserveOrThrowPassesWhenAVacancyIsReserved() {
        when(repository.tryIncrement(DAY, 250)).thenReturn(true);

        assertDoesNotThrow(
                () -> serviceAt(NOON).reserveOrThrow(EmailPriority.CRITICAL));
    }

    @Test
    void reserveOrThrowFailsWithTheStableCodeAndTimeUntilMidnightUtc() {
        when(repository.tryIncrement(DAY, 200)).thenReturn(false);

        EmailQuotaExceededException exception = assertThrows(
                EmailQuotaExceededException.class,
                () -> serviceAt(NOON).reserveOrThrow(EmailPriority.STANDARD));

        assertEquals("EMAIL_QUOTA_EXCEEDED", exception.getCode());
        assertEquals(12 * 3600, exception.getRetryAfterSeconds());
    }

    @Test
    void retryAfterIsAtLeastOneSecondRightBeforeMidnight() {
        when(repository.tryIncrement(DAY, 200)).thenReturn(false);

        EmailQuotaExceededException exception = assertThrows(
                EmailQuotaExceededException.class,
                () -> serviceAt(Instant.parse("2026-11-25T23:59:59.900Z"))
                        .reserveOrThrow(EmailPriority.STANDARD));

        assertEquals(1, exception.getRetryAfterSeconds());
    }

    // ---------- dia ----------

    @Test
    void theDayIsTheUtcDayEvenWhenTheClockZoneIsDifferent() {
        // 01:00 UTC do dia 25 ainda é dia 24 em São Paulo.
        Clock saoPaulo = Clock.fixed(
                Instant.parse("2026-11-25T01:00:00Z"),
                ZoneId.of("America/Sao_Paulo"));
        when(repository.reservedOn(DAY)).thenReturn(0);

        assertDoesNotThrow(
                () -> serviceAt(saoPaulo).checkCapacity(EmailPriority.STANDARD));
        verify(repository).reservedOn(DAY);
    }
}
