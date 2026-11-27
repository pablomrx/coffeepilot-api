package com.coffeepilot.api.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailQuotaPropertiesTest {

    @Test
    void standardLimitIsTheTotalMinusTheCriticalReserve() {
        EmailQuotaProperties properties = new EmailQuotaProperties(250, 50);

        assertEquals(200, properties.standardLimit());
        assertEquals(250, properties.criticalLimit());
    }

    @Test
    void zeroReserveGivesStandardTheWholeLimit() {
        EmailQuotaProperties properties = new EmailQuotaProperties(250, 0);

        assertEquals(250, properties.standardLimit());
        assertEquals(250, properties.criticalLimit());
    }

    @Test
    void rejectsReserveEqualToTheTotal() {
        assertThrows(IllegalArgumentException.class,
                () -> new EmailQuotaProperties(250, 250));
    }

    @Test
    void rejectsReserveGreaterThanTheTotal() {
        assertThrows(IllegalArgumentException.class,
                () -> new EmailQuotaProperties(100, 150));
    }

    @Test
    void rejectsZeroTotal() {
        assertThrows(IllegalArgumentException.class,
                () -> new EmailQuotaProperties(0, 0));
    }
}
