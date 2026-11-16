package com.coffeepilot.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coffeepilot.api.common.exception.TooManyLoginAttemptsException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

class LoginAttemptLimiterTest {

    private static final String EMAIL = "dono@teste.com";

    private AtomicLong time;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        time = new AtomicLong(1_000_000L);
        limiter = new LoginAttemptLimiter(3, Duration.ofMinutes(15), time::get);
    }

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(email);
        }
    }

    @Test
    void semFalhasNaoBloqueia() {
        assertThatCode(() -> limiter.checkAllowed(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void abaixoDoLimiteNaoBloqueia() {
        fail(EMAIL, 2);

        assertThatCode(() -> limiter.checkAllowed(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void noLimiteBloqueiaComRetryAfterDaJanelaInteira() {
        fail(EMAIL, 3);

        assertThatThrownBy(() -> limiter.checkAllowed(EMAIL))
                .isInstanceOfSatisfying(TooManyLoginAttemptsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(900));
    }

    @Test
    void retryAfterDiminuiComOTempoENuncaEMenorQueUm() {
        fail(EMAIL, 3);

        time.addAndGet(600_000);
        assertThatThrownBy(() -> limiter.checkAllowed(EMAIL))
                .isInstanceOfSatisfying(TooManyLoginAttemptsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(300));

        time.addAndGet(299_500);
        assertThatThrownBy(() -> limiter.checkAllowed(EMAIL))
                .isInstanceOfSatisfying(TooManyLoginAttemptsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(1));
    }

    @Test
    void reabreAposAJanela() {
        fail(EMAIL, 3);

        time.addAndGet(900_000);

        assertThatCode(() -> limiter.checkAllowed(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void falhaAposJanelaExpiradaReiniciaOContador() {
        fail(EMAIL, 3);
        time.addAndGet(900_000);

        limiter.recordFailure(EMAIL);

        assertThatCode(() -> limiter.checkAllowed(EMAIL)).doesNotThrowAnyException();
        fail(EMAIL, 1);
        assertThatCode(() -> limiter.checkAllowed(EMAIL)).doesNotThrowAnyException();
        fail(EMAIL, 1);
        assertThatThrownBy(() -> limiter.checkAllowed(EMAIL))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void sucessoZeraOContador() {
        fail(EMAIL, 2);

        limiter.recordSuccess(EMAIL);
        fail(EMAIL, 2);

        assertThatCode(() -> limiter.checkAllowed(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void emailsDistintosSaoIndependentes() {
        fail(EMAIL, 3);

        assertThatThrownBy(() -> limiter.checkAllowed(EMAIL))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatCode(() -> limiter.checkAllowed("outro@teste.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void caixaEEspacosCaemNaMesmaChave() {
        limiter.recordFailure("  DONO@Teste.com ");
        limiter.recordFailure("dono@teste.com");
        limiter.recordFailure("Dono@TESTE.COM");

        assertThatThrownBy(() -> limiter.checkAllowed("DONO@TESTE.COM"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThat(limiter.size()).isEqualTo(1);
    }

    @Test
    void emailInexistenteTambemConta() {
        fail("naoexiste@teste.com", 3);

        assertThatThrownBy(() -> limiter.checkAllowed("naoexiste@teste.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void emailNuloOuVazioNaoQuebra() {
        assertThatCode(() -> {
            limiter.recordFailure(null);
            limiter.checkAllowed(null);
            limiter.recordSuccess(null);
        }).doesNotThrowAnyException();
    }

    @Test
    void emailMuitoLongoEhTruncadoNaChave() {
        String longEmail = "a".repeat(500) + "@teste.com";

        limiter.recordFailure(longEmail);
        limiter.recordFailure(longEmail + "x");
        limiter.recordFailure(longEmail + "y");

        assertThat(limiter.size()).isEqualTo(1);
    }

    @Test
    void relogioQueVoltaNoTempoTrataComoJanelaExpirada() {
        fail(EMAIL, 3);

        time.addAndGet(-1_000);

        assertThatCode(() -> limiter.checkAllowed(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void evictExpiredRemoveSomenteJanelasVencidas() {
        limiter.recordFailure("velho@teste.com");
        time.addAndGet(600_000);
        limiter.recordFailure("novo@teste.com");
        time.addAndGet(300_000);

        limiter.evictExpired();

        assertThat(limiter.size()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void maxFalhasInvalidoImpedeACriacao(int max) {
        assertThatThrownBy(() ->
                new LoginAttemptLimiter(max, Duration.ofMinutes(15), time::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("login-failures-per-email");
    }

    @Test
    void janelaInvalidaImpedeACriacao() {
        assertThatThrownBy(() ->
                new LoginAttemptLimiter(3, Duration.ofSeconds(59), time::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("login-failures-window");
        assertThatThrownBy(() ->
                new LoginAttemptLimiter(3, Duration.ofHours(25), time::get))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
                new LoginAttemptLimiter(3, null, time::get))
                .isInstanceOf(NullPointerException.class);
    }
}
