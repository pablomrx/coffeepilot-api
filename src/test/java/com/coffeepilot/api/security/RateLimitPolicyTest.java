package com.coffeepilot.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

class RateLimitPolicyTest {

    private final AtomicLong time = new AtomicLong(1_000_000L);

    private RateLimitProperties props(List<RateLimitProperties.Rule> rules) {
        return new RateLimitProperties(5, 3, 120, 100, rules);
    }

    private RateLimitProperties.Rule rule(
            String method, String path, int max, String window) {
        return new RateLimitProperties.Rule(
                method, path, max, window == null ? null : Duration.parse(window));
    }

    private RateLimitPolicy policy(RateLimitProperties.Rule... rules) {
        return new RateLimitPolicy(props(List.of(rules)), time::get);
    }

    // ---------- validação (a aplicação não pode subir com erro) ----------

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 21})
    void loginForaDoIntervaloImpedeASubida(int value) {
        assertThatThrownBy(() -> new RateLimitPolicy(
                new RateLimitProperties(value, 3, 120, 100, List.of()), time::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("login-per-minute");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 21})
    void registerForaDoIntervaloImpedeASubida(int value) {
        assertThatThrownBy(() -> new RateLimitPolicy(
                new RateLimitProperties(5, value, 120, 100, List.of()), time::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("register-per-hour");
    }

    @Test
    void limiteGeralInvalidoImpedeASubida() {
        assertThatThrownBy(() -> new RateLimitPolicy(
                new RateLimitProperties(5, 3, 0, 100, List.of()), time::get))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RateLimitPolicy(
                new RateLimitProperties(5, 3, 100_001, 100, List.of()), time::get))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void regrasInvalidasImpedemASubida() {
        assertThatThrownBy(() -> policy(rule("POST", null, 10, "PT1M")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy(rule("POST", "/orders", 10, "PT1M")))
                .hasMessageContaining("/v1/");
        assertThatThrownBy(() -> policy(rule("POST", "/v1/or*ders", 10, "PT1M")))
                .hasMessageContaining("curinga");
        assertThatThrownBy(() -> policy(rule("POST", "/v1/orders?x=1", 10, "PT1M")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy(rule("FETCH", "/v1/orders", 10, "PT1M")))
                .hasMessageContaining("método");
        assertThatThrownBy(() -> policy(rule("POST", "/v1/orders", 0, "PT1M")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy(rule("POST", "/v1/orders", 100_001, "PT1M")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy(rule("POST", "/v1/orders", 10, null)))
                .hasMessageContaining("janela");
        assertThatThrownBy(() -> policy(rule("POST", "/v1/orders", 10, "PT0.5S")))
                .hasMessageContaining("janela");
        assertThatThrownBy(() -> policy(rule("POST", "/v1/orders", 10, "P2D")))
                .hasMessageContaining("janela");
    }

    @Test
    void regraDuplicadaImpedeASubida() {
        assertThatThrownBy(() -> policy(
                rule("POST", "/v1/orders", 10, "PT1M"),
                rule("post", "/v1/orders/", 20, "PT1M")))
                .hasMessageContaining("duplicada");
    }

    @Test
    void regraNaoPodeTerLoginNemRegisterComoCaminhoExato() {
        assertThatThrownBy(() -> policy(rule(null, "/v1/auth/login", 1000, "PT1M")))
                .hasMessageContaining("fixo");
        assertThatThrownBy(() -> policy(rule(null, "/v1/auth/register", 1000, "PT1M")))
                .hasMessageContaining("fixo");
    }

    // ---------- resolução ----------

    @Test
    void loginERegisterNuncaSaoAfrouxadosPorCuringa() {
        RateLimitPolicy policy = policy(rule(null, "/v1/auth/**", 5000, "PT1M"));

        // O limite de login (5) vale, não o curinga (5000).
        for (int i = 0; i < 5; i++) {
            assertThat(policy.resolve("POST", RateLimitPolicy.LOGIN_PATH)
                    .tryAcquire("ip").allowed()).isTrue();
        }
        assertThat(policy.resolve("POST", RateLimitPolicy.LOGIN_PATH)
                .tryAcquire("ip").allowed()).isFalse();
    }

    @Test
    void semRegraCorrespondenteVaiParaOLimiteGeral() {
        RateLimitPolicy policy = policy(rule("POST", "/v1/orders", 1, "PT1M"));

        RateLimiter general = policy.resolve("GET", "/v1/qualquer");

        assertThat(policy.resolve("GET", "/v1/outra")).isSameAs(general);
    }

    @Test
    void metodoDiferenteNaoCasaARegra() {
        RateLimitPolicy policy = policy(rule("POST", "/v1/orders", 1, "PT1M"));

        assertThat(policy.resolve("POST", "/v1/orders"))
                .isNotSameAs(policy.resolve("GET", "/v1/orders"));
    }

    @Test
    void exatoVenceCuringaIndependenteDaOrdemEscrita() {
        RateLimitProperties.Rule wildcard = rule(null, "/v1/orders/**", 100, "PT1M");
        RateLimitProperties.Rule exact = rule(null, "/v1/orders/summary", 2, "PT1M");

        // Curinga escrito primeiro de propósito.
        RateLimitPolicy policy = policy(wildcard, exact);

        RateLimiter forExact = policy.resolve("GET", "/v1/orders/summary");
        RateLimiter forOther = policy.resolve("GET", "/v1/orders/5");

        assertThat(forExact).isNotSameAs(forOther);
        assertThat(forExact.tryAcquire("ip").allowed()).isTrue();
        assertThat(forExact.tryAcquire("ip").allowed()).isTrue();
        assertThat(forExact.tryAcquire("ip").allowed()).isFalse();
        assertThat(forOther.tryAcquire("ip").allowed()).isTrue();
    }

    @Test
    void curingaMaisLongoVenceOMaisCurto() {
        RateLimitPolicy policy = policy(
                rule(null, "/v1/**", 100, "PT1M"),
                rule(null, "/v1/orders/**", 10, "PT1M"));

        assertThat(policy.resolve("GET", "/v1/orders/7"))
                .isNotSameAs(policy.resolve("GET", "/v1/products"));
    }

    @Test
    void metodoEspecificoVenceQualquerMetodo() {
        RateLimitPolicy policy = policy(
                rule(null, "/v1/orders", 100, "PT1M"),
                rule("POST", "/v1/orders", 3, "PT1M"));

        assertThat(policy.resolve("POST", "/v1/orders"))
                .isNotSameAs(policy.resolve("GET", "/v1/orders"));
    }

    @Test
    void curingaCasaABaseEOsSubcaminhosMasNaoPrefixosParecidos() {
        RateLimitPolicy policy = policy(rule(null, "/v1/orders/**", 1, "PT1M"));

        RateLimiter general = policy.resolve("GET", "/v1/products");

        assertThat(policy.resolve("GET", "/v1/orders"))
                .isNotSameAs(general);
        assertThat(policy.resolve("GET", "/v1/orders/9/start"))
                .isNotSameAs(general);
        assertThat(policy.resolve("GET", "/v1/ordersx")).isSameAs(general);
    }

    @Test
    void metodoNaoDependeDeCaixa() {
        RateLimitPolicy policy = policy(rule("post", "/v1/orders", 1, "PT1M"));

        assertThat(policy.resolve("POST", "/v1/orders"))
                .isSameAs(policy.resolve("post", "/v1/orders"));
    }
}
