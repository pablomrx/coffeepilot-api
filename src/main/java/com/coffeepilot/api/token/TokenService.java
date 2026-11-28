package com.coffeepilot.api.token;

import com.coffeepilot.api.common.exception.InvalidTokenException;
import com.coffeepilot.api.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class TokenService {

    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private static final int TOKEN_BYTES = 32;

    private final UserTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(
            UserTokenRepository tokenRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public Optional<String> issue(Long userId, TokenType type) {
        lockUser(userId);

        OffsetDateTime now = OffsetDateTime.now(clock);

        boolean coolingDown = tokenRepository
                .findFirstByUserIdAndTypeOrderByCreatedAtDesc(userId, type)
                .map(last -> last.getCreatedAt().isAfter(now.minus(RESEND_COOLDOWN)))
                .orElse(false);

        if (coolingDown) {
            return Optional.empty();
        }

        tokenRepository.revokeActive(userId, type, now);

        String rawToken = generateRawToken();

        tokenRepository.save(new UserToken(
                userId,
                type,
                hash(rawToken),
                now,
                now.plus(type.validity())
        ));

        return Optional.of(rawToken);
    }

    @Transactional
    public Long consume(String rawToken, TokenType type) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException();
        }

        String hash = hash(rawToken.trim());
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (tokenRepository.consume(hash, type, now) != 1) {
            throw new InvalidTokenException();
        }

        return tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalStateException(
                        "Token consumido não encontrado."
                ))
                .getUserId();
    }

    @Transactional
    public void revokeAll(Long userId) {
        lockUser(userId);

        tokenRepository.revokeAllActive(userId, OffsetDateTime.now(clock));
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Usuário inexistente."
                ));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
