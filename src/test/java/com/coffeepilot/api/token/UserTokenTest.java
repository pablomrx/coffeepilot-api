package com.coffeepilot.api.token;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTokenTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-11-26T12:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Test
    void storesTheGivenValuesAndStartsUnused() {
        UserToken token = new UserToken(
                7L, TokenType.PASSWORD_RESET, HASH, NOW, NOW.plusMinutes(30));

        assertEquals(7L, token.getUserId());
        assertEquals(TokenType.PASSWORD_RESET, token.getType());
        assertEquals(HASH, token.getTokenHash());
        assertEquals(NOW, token.getCreatedAt());
        assertEquals(NOW.plusMinutes(30), token.getExpiresAt());
        assertNull(token.getUsedAt());
        assertNull(token.getRevokedAt());
    }

    @Test
    void rejectsExpirationEqualToCreation() {
        assertThrows(IllegalArgumentException.class, () -> new UserToken(
                7L, TokenType.PASSWORD_RESET, HASH, NOW, NOW));
    }

    @Test
    void rejectsExpirationBeforeCreation() {
        assertThrows(IllegalArgumentException.class, () -> new UserToken(
                7L, TokenType.PASSWORD_RESET, HASH, NOW, NOW.minusSeconds(1)));
    }

    @Test
    void rejectsBlankHash() {
        assertThrows(IllegalArgumentException.class, () -> new UserToken(
                7L, TokenType.PASSWORD_RESET, "  ", NOW, NOW.plusMinutes(30)));
        assertThrows(IllegalArgumentException.class, () -> new UserToken(
                7L, TokenType.PASSWORD_RESET, null, NOW, NOW.plusMinutes(30)));
    }

    @Test
    void rejectsHashWithTheWrongFormat() {
        String[] invalid = {
                "a".repeat(63),
                "a".repeat(65),
                "A".repeat(64),
                "g".repeat(64),
                "a".repeat(63) + " "
        };

        for (String hash : invalid) {
            assertThrows(IllegalArgumentException.class, () -> new UserToken(
                    7L, TokenType.PASSWORD_RESET, hash, NOW, NOW.plusMinutes(30)));
        }
    }

    @Test
    void acceptsAnSha256HexHash() {
        UserToken token = new UserToken(
                7L, TokenType.PASSWORD_RESET, TokenService.hash("abc"), NOW,
                NOW.plusMinutes(30));

        assertEquals(64, token.getTokenHash().length());
    }

    @Test
    void rejectsNullValues() {
        assertThrows(NullPointerException.class, () -> new UserToken(
                null, TokenType.PASSWORD_RESET, HASH, NOW, NOW.plusMinutes(30)));
        assertThrows(NullPointerException.class, () -> new UserToken(
                7L, null, HASH, NOW, NOW.plusMinutes(30)));
        assertThrows(NullPointerException.class, () -> new UserToken(
                7L, TokenType.PASSWORD_RESET, HASH, null, NOW.plusMinutes(30)));
        assertThrows(NullPointerException.class, () -> new UserToken(
                7L, TokenType.PASSWORD_RESET, HASH, NOW, null));
    }

    @Test
    void validityOfEachTokenType() {
        assertEquals(Duration.ofHours(24), TokenType.EMAIL_CONFIRMATION.validity());
        assertEquals(Duration.ofMinutes(30), TokenType.PASSWORD_RESET.validity());
        assertEquals(Duration.ofHours(72), TokenType.EMPLOYEE_INVITE.validity());
    }
}
