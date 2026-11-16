package com.coffeepilot.api.security;

import com.coffeepilot.api.common.exception.TooManyLoginAttemptsException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Limita falhas de login por e-mail normalizado, exista a conta ou não.
 * Em memória: reinício zera e não há compartilhamento entre instâncias.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES_CEILING = 100;
    static final Duration MIN_WINDOW = Duration.ofMinutes(1);
    static final Duration MAX_WINDOW = Duration.ofHours(24);
    private static final int MAX_KEYS = 100_000;
    private static final int MAX_KEY_LENGTH = 254;

    private record Attempts(long startMillis, int failures) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final long windowMillis;
    private final LongSupplier clock;

    @Autowired
    public LoginAttemptLimiter(
            @Value("${rate-limit.login-failures-per-email:10}") int maxFailures,
            @Value("${rate-limit.login-failures-window:15m}") Duration window
    ) {
        this(maxFailures, window, System::currentTimeMillis);
    }

    public LoginAttemptLimiter(int maxFailures, Duration window, LongSupplier clock) {
        if (maxFailures <= 0 || maxFailures > MAX_FAILURES_CEILING) {
            throw new IllegalStateException(
                    "rate-limit.login-failures-per-email deve estar entre 1 e "
                            + MAX_FAILURES_CEILING + ".");
        }

        Objects.requireNonNull(window, "window é obrigatória");

        if (window.compareTo(MIN_WINDOW) < 0 || window.compareTo(MAX_WINDOW) > 0) {
            throw new IllegalStateException(
                    "rate-limit.login-failures-window deve estar entre 1 minuto e 24 horas.");
        }

        this.maxFailures = maxFailures;
        this.windowMillis = window.toMillis();
        this.clock = Objects.requireNonNull(clock);
    }

    // Chamar ANTES de verificar a senha.
    public void checkAllowed(String email) {
        String key = key(email);
        long now = clock.getAsLong();
        Attempts current = attempts.get(key);

        if (current == null || isExpired(current, now)) {
            return;
        }

        if (current.failures() >= maxFailures) {
            long remaining = Math.max(0, windowMillis - (now - current.startMillis()));
            throw new TooManyLoginAttemptsException(
                    Math.max(1, 1 + (remaining - 1) / 1000));
        }
    }

    public void recordFailure(String email) {
        String key = key(email);
        long now = clock.getAsLong();

        if (!attempts.containsKey(key) && attempts.size() >= MAX_KEYS) {
            evictExpired();

            if (attempts.size() >= MAX_KEYS) {
                return;
            }
        }

        attempts.compute(key, (k, current) ->
                current == null || isExpired(current, now)
                        ? new Attempts(now, 1)
                        : new Attempts(current.startMillis(), current.failures() + 1));
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    @Scheduled(fixedDelayString = "${rate-limit.cleanup-interval-ms:60000}")
    public void evictExpired() {
        long now = clock.getAsLong();
        attempts.entrySet().removeIf(e -> isExpired(e.getValue(), now));
    }

    int size() {
        return attempts.size();
    }

    private static String key(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();

        return normalized.length() > MAX_KEY_LENGTH
                ? normalized.substring(0, MAX_KEY_LENGTH)
                : normalized;
    }

    private boolean isExpired(Attempts a, long now) {
        long elapsed = now - a.startMillis();
        return elapsed < 0 || elapsed >= windowMillis;
    }
}
