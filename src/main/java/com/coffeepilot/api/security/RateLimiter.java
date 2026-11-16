package com.coffeepilot.api.security;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public class RateLimiter {

    public static final int DEFAULT_MAX_KEYS = 100_000;

    private static final long MIN_EVICTION_INTERVAL_MILLIS = 1_000;

    public record Result(boolean allowed, long retryAfterSeconds) {
    }

    private record Window(long startMillis, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Object capacityLock = new Object();

    private final int maxRequests;
    private final long windowMillis;
    private final int maxKeys;
    private final LongSupplier clock;

    private long lastEvictionMillis = Long.MIN_VALUE / 2;

    public RateLimiter(
            int maxRequests,
            Duration window,
            int maxKeys,
            LongSupplier clock
    ) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException(
                    "maxRequests deve ser maior que zero.");
        }

        Objects.requireNonNull(window, "window é obrigatória");

        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException(
                    "A janela deve ser maior que zero.");
        }

        if (maxKeys <= 0) {
            throw new IllegalArgumentException(
                    "maxKeys deve ser maior que zero.");
        }

        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.maxKeys = maxKeys;
        this.clock = Objects.requireNonNull(clock, "clock é obrigatório");

        if (this.windowMillis <= 0) {
            throw new IllegalArgumentException(
                    "A janela deve ter ao menos 1 milissegundo.");
        }
    }

    public Result tryAcquire(String key) {
        Objects.requireNonNull(key, "key é obrigatória");

        long now = clock.getAsLong();
        Result[] outcome = new Result[1];

        windows.computeIfPresent(
                key, (k, current) -> update(current, now, outcome));

        if (outcome[0] != null) {
            return outcome[0];
        }

        synchronized (capacityLock) {

            windows.computeIfPresent(
                    key, (k, current) -> update(current, now, outcome));

            if (outcome[0] != null) {
                return outcome[0];
            }

            if (windows.size() >= maxKeys) {
                long sinceLast = now - lastEvictionMillis;

                if (sinceLast < 0 || sinceLast >= MIN_EVICTION_INTERVAL_MILLIS) {
                    evictExpiredLocked(now);
                    lastEvictionMillis = now;
                }

                if (windows.size() >= maxKeys) {
                    return new Result(false, ceilSeconds(windowMillis));
                }
            }

            windows.put(key, new Window(now, 1));

            return new Result(true, 0);
        }
    }

    public void evictExpired() {
        long now = clock.getAsLong();

        synchronized (capacityLock) {
            evictExpiredLocked(now);
        }
    }

    public int size() {
        return windows.size();
    }

    private Window update(Window current, long now, Result[] outcome) {
        if (isExpired(current, now)) {
            outcome[0] = new Result(true, 0);
            return new Window(now, 1);
        }

        if (current.count() >= maxRequests) {
            long elapsed = Math.max(0, now - current.startMillis());
            long remaining = Math.max(0, windowMillis - elapsed);

            outcome[0] = new Result(false, ceilSeconds(remaining));
            return current;
        }

        outcome[0] = new Result(true, 0);
        return new Window(current.startMillis(), current.count() + 1);
    }

    private void evictExpiredLocked(long now) {
        windows.entrySet().removeIf(
                entry -> isExpired(entry.getValue(), now));
    }

    private long ceilSeconds(long millis) {
        if (millis <= 0) {
            return 1;
        }

        return 1 + (millis - 1) / 1000;
    }

    private boolean isExpired(Window window, long now) {
        long elapsed = now - window.startMillis();

        return elapsed < 0 || elapsed >= windowMillis;
    }
}
