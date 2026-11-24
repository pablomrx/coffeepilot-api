package com.coffeepilot.api.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityConfigCorsTest {

    @Test
    void parsesSingleOrigin() {
        assertEquals(
                List.of("http://localhost:5173"),
                SecurityConfig.parseAllowedOrigins("http://localhost:5173")
        );
    }

    @Test
    void parsesMultipleOriginsTrimmingSpacesAndIgnoringEmptyItems() {
        assertEquals(
                List.of("https://a.example.com", "https://b.example.com"),
                SecurityConfig.parseAllowedOrigins(
                        " https://a.example.com , ,https://b.example.com,"
                )
        );
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalStateException.class,
                () -> SecurityConfig.parseAllowedOrigins(null));
    }

    @Test
    void rejectsEmptyString() {
        assertThrows(IllegalStateException.class,
                () -> SecurityConfig.parseAllowedOrigins(""));
    }

    @Test
    void rejectsOnlySpacesAndCommas() {
        assertThrows(IllegalStateException.class,
                () -> SecurityConfig.parseAllowedOrigins(" , ,, "));
    }

    @Test
    void rejectsWildcard() {
        assertThrows(IllegalStateException.class,
                () -> SecurityConfig.parseAllowedOrigins("*"));
    }

    @Test
    void rejectsWildcardAmongValidOrigins() {
        assertThrows(IllegalStateException.class,
                () -> SecurityConfig.parseAllowedOrigins(
                        "https://app.example.com,*"
                ));
    }

    @Test
    void rejectsWildcardInsideOrigin() {
        assertThrows(IllegalStateException.class,
                () -> SecurityConfig.parseAllowedOrigins(
                        "https://*.example.com"
                ));
    }
}
