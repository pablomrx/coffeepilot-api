package com.coffeepilot.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class RateLimiterTest {

    private AtomicLong time;
    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        time = new AtomicLong(1_000_000L);
        limiter = new RateLimiter(3, Duration.ofMinutes(1), 1000, time::get);
    }

    @Test
    void permiteAteOLimiteEBloqueiaOSeguinte() {
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();

        RateLimiter.Result blocked = limiter.tryAcquire("a");

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void retryAfterDiminuiComOPassarDoTempoENuncaEMenorQueUm() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }

        time.addAndGet(45_000);
        assertThat(limiter.tryAcquire("a").retryAfterSeconds()).isEqualTo(15);

        time.addAndGet(14_999);
        assertThat(limiter.tryAcquire("a").retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void reabreAposAJanela() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();

        time.addAndGet(60_000);

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
    }

    @Test
    void chavesDistintasSaoIndependentes() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }

        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void relogioQueVoltaNoTempoReiniciaAJanela() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }

        time.addAndGet(-5_000);

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
    }

    @Test
    void evictExpiredRemoveSomenteJanelasVencidas() {
        limiter.tryAcquire("velha");
        time.addAndGet(30_000);
        limiter.tryAcquire("nova");
        time.addAndGet(30_000);

        limiter.evictExpired();

        assertThat(limiter.size()).isEqualTo(1);
        assertThat(limiter.tryAcquire("nova").allowed()).isTrue();
    }

    @Test
    void comMapaCheioRecusaChaveNovaEAceitaDepoisDeExpirar() {
        RateLimiter small =
                new RateLimiter(5, Duration.ofMinutes(1), 2, time::get);

        assertThat(small.tryAcquire("a").allowed()).isTrue();
        assertThat(small.tryAcquire("b").allowed()).isTrue();
        assertThat(small.tryAcquire("c").allowed()).isFalse();
        assertThat(small.tryAcquire("a").allowed()).isTrue();

        time.addAndGet(60_000);

        assertThat(small.tryAcquire("c").allowed()).isTrue();
    }

    @Test
    void concorrenciaPermiteExatamenteOMaximo() throws Exception {
        RateLimiter shared =
                new RateLimiter(50, Duration.ofMinutes(1), 1000, time::get);

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();

        for (int i = 0; i < 400; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (shared.tryAcquire("mesmo-ip").allowed()) {
                    allowed.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();

        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(allowed.get()).isEqualTo(50);
    }

    @Test
    void concorrenciaComChavesDistintasNuncaUltrapassaMaxKeys() throws Exception {
        RateLimiter small =
                new RateLimiter(5, Duration.ofMinutes(1), 50, time::get);

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();

        for (int i = 0; i < 500; i++) {
            String key = "ip-" + i;

            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (small.tryAcquire(key).allowed()) {
                    allowed.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();

        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(small.size()).isEqualTo(50);
        assertThat(allowed.get()).isEqualTo(50);
    }

    @Test
    void validaParametrosDoConstrutor() {
        assertThatThrownBy(() ->
                new RateLimiter(0, Duration.ofMinutes(1), 10, time::get))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new RateLimiter(-1, Duration.ofMinutes(1), 10, time::get))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new RateLimiter(1, Duration.ZERO, 10, time::get))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new RateLimiter(1, Duration.ofSeconds(-1), 10, time::get))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new RateLimiter(1, null, 10, time::get))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() ->
                new RateLimiter(1, Duration.ofMinutes(1), 0, time::get))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new RateLimiter(1, Duration.ofMinutes(1), 10, null))
                .isInstanceOf(NullPointerException.class);
    }
}
