package com.coffeepilot.api.security;

import com.coffeepilot.api.establishment.Establishment;
import com.coffeepilot.api.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final int MIN_KEY_BYTES = 32;
    private static final String TOKEN_VERSION_CLAIM = "tv";

    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final Clock clock;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-seconds}") long expirationSeconds,
            Clock clock
    ) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);

        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET deve ter pelo menos 256 bits (32 bytes) em Base64."
            );
        }

        if (expirationSeconds <= 0) {
            throw new IllegalStateException(
                    "JWT_EXPIRATION_SECONDS deve ser maior que zero."
            );
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationSeconds = expirationSeconds;
        this.clock = clock;
    }

    public record TokenClaims(long userId, int tokenVersion) {
    }

    public String generateToken(User user) {
        Instant now = clock.instant();
        Instant expiration = expirationFor(user, now);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            Integer tokenVersion =
                    claims.get(TOKEN_VERSION_CLAIM, Integer.class);

            if (subject == null
                    || subject.isBlank()
                    || tokenVersion == null
                    || tokenVersion < 0) {
                return Optional.empty();
            }

            return Optional.of(
                    new TokenClaims(Long.parseLong(subject), tokenVersion)
            );

        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    public long expirationSecondsFor(User user) {
        Instant now = clock.instant();

        return Duration.between(now, expirationFor(user, now)).getSeconds();
    }

    private Instant expirationFor(User user, Instant now) {
        Instant expiration = now.plusSeconds(expirationSeconds);
        Establishment establishment = user.getEstablishment();

        if (establishment.isDemo()) {
            if (establishment.getDemoExpiresAt() == null) {
                throw new IllegalStateException(
                        "Estabelecimento de demonstração sem data de expiração."
                );
            }

            Instant demoExpiration = establishment.getDemoExpiresAt().toInstant();

            if (!demoExpiration.isAfter(now)) {
                throw new IllegalStateException(
                        "A demonstração já expirou."
                );
            }

            if (demoExpiration.isBefore(expiration)) {
                expiration = demoExpiration;
            }
        }

        return expiration;
    }
}
