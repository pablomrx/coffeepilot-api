package com.coffeepilot.api.security;

import com.coffeepilot.api.establishment.Establishment;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
    );
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString(
            "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8)
    );
    private static final Instant START = Instant.parse("2026-11-21T12:00:00Z");
    private static final long TTL_SECONDS = 28800;

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static JwtService serviceAt(Instant instant) {
        return new JwtService(SECRET, TTL_SECONDS, clockAt(instant));
    }

    private static User user(Establishment establishment, long id) {
        User user = new User(
                establishment, "Ana", "ana@example.com", "hash", Role.OWNER
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static User realUser(long id) {
        return user(new Establishment("Cafeteria"), id);
    }

    private static User demoUser(long id, Duration demoTtl) {
        return user(
                Establishment.demo("Demo", demoTtl, clockAt(START)),
                id
        );
    }

    @Test
    void rejectsShortSecret() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[16]);

        assertThrows(IllegalStateException.class,
                () -> new JwtService(shortSecret, TTL_SECONDS, clockAt(START)));
    }

    @Test
    void rejectsNonPositiveExpiration() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService(SECRET, 0, clockAt(START)));
    }

    @Test
    void tokenRoundTripKeepsUserIdAndTokenVersion() {
        JwtService service = serviceAt(START);

        String token = service.generateToken(realUser(7L));

        JwtService.TokenClaims claims = service.parse(token).orElseThrow();
        assertEquals(7L, claims.userId());
        assertEquals(0, claims.tokenVersion());
    }

    @Test
    void tokenCarriesCurrentTokenVersion() {
        JwtService service = serviceAt(START);
        User user = realUser(7L);
        user.invalidateSessions();
        user.invalidateSessions();

        String token = service.generateToken(user);

        assertEquals(2, service.parse(token).orElseThrow().tokenVersion());
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = serviceAt(START);
        String token = service.generateToken(realUser(7L));
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertEquals(Optional.empty(), service.parse(tampered));
    }

    @Test
    void rejectsGarbage() {
        assertEquals(Optional.empty(), serviceAt(START).parse("não-é-um-jwt"));
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtService other = new JwtService(OTHER_SECRET, TTL_SECONDS, clockAt(START));
        String token = other.generateToken(realUser(7L));

        assertEquals(Optional.empty(), serviceAt(START).parse(token));
    }

    @Test
    void rejectsTokenWithoutTokenVersion() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        String token = Jwts.builder()
                .subject("7")
                .issuedAt(Date.from(START))
                .expiration(Date.from(START.plusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertEquals(Optional.empty(), serviceAt(START).parse(token));
    }

    @Test
    void rejectsTokenWithNegativeTokenVersion() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        String token = Jwts.builder()
                .subject("7")
                .claim("tv", -1)
                .issuedAt(Date.from(START))
                .expiration(Date.from(START.plusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertEquals(Optional.empty(), serviceAt(START).parse(token));
    }

    @Test
    void refusesToIssueTokenForDemoWithoutExpiration() {
        Establishment establishment = new Establishment("Demo");
        ReflectionTestUtils.setField(establishment, "demo", true);
        User user = user(establishment, 7L);

        assertThrows(IllegalStateException.class,
                () -> serviceAt(START).generateToken(user));
        assertThrows(IllegalStateException.class,
                () -> serviceAt(START).expirationSecondsFor(user));
    }

    @Test
    void tokenIsValidBeforeExpirationAndInvalidAfter() {
        String token = serviceAt(START).generateToken(realUser(7L));

        assertTrue(serviceAt(START.plusSeconds(TTL_SECONDS - 60))
                .parse(token).isPresent());
        assertEquals(Optional.empty(),
                serviceAt(START.plusSeconds(TTL_SECONDS + 60)).parse(token));
    }

    @Test
    void realUserExpirationIsTheDefault() {
        assertEquals(TTL_SECONDS,
                serviceAt(START).expirationSecondsFor(realUser(7L)));
    }

    @Test
    void demoTokenNeverOutlivesTheSandbox() {
        User user = demoUser(7L, Duration.ofMinutes(45));
        String token = serviceAt(START).generateToken(user);

        assertEquals(Duration.ofMinutes(45).getSeconds(),
                serviceAt(START).expirationSecondsFor(user));
        assertTrue(serviceAt(START.plus(Duration.ofMinutes(44)))
                .parse(token).isPresent());
        assertEquals(Optional.empty(),
                serviceAt(START.plus(Duration.ofMinutes(46))).parse(token));
    }

    @Test
    void demoLongerThanDefaultIsCappedAtDefault() {
        User user = demoUser(7L, Duration.ofHours(12));

        assertEquals(TTL_SECONDS, serviceAt(START).expirationSecondsFor(user));
    }

    @Test
    void refusesToIssueTokenForExpiredSandbox() {
        User user = demoUser(7L, Duration.ofMinutes(45));

        assertThrows(IllegalStateException.class,
                () -> serviceAt(START.plus(Duration.ofHours(1)))
                        .generateToken(user));
    }
}
