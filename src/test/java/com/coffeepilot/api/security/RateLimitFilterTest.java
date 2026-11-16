package com.coffeepilot.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class RateLimitFilterTest {

    private AtomicLong time;
    private AtomicInteger chainCalls;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        time = new AtomicLong(1_000_000L);
        chainCalls = new AtomicInteger();

        // login 2/min, register 1/h, geral 3/min, POST /v1/orders 1/min
        RateLimitProperties properties = new RateLimitProperties(
                2, 1, 3, 100,
                List.of(new RateLimitProperties.Rule(
                        "POST", "/v1/orders", 1, Duration.ofMinutes(1))));

        filter = new RateLimitFilter(
                new RateLimitPolicy(properties, time::get),
                JsonMapper.builder().build());
    }

    private MockHttpServletResponse call(String method, String path, String ip)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(ip);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response,
                (req, res) -> chainCalls.incrementAndGet());

        return response;
    }

    @Test
    void bloqueiaLoginAposOLimiteComRetryAfterEJson() throws Exception {
        assertThat(call("POST", "/v1/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/v1/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = call("POST", "/v1/auth/login", "1.1.1.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentType()).startsWith("application/json");
        assertThat(blocked.getContentAsString())
                .contains("\"status\":429")
                .contains("\"error\":\"Too Many Requests\"")
                .contains("\"path\":\"/v1/auth/login\"")
                .contains("\"fieldErrors\":[]")
                .contains("\"timestamp\"");
    }

    @Test
    void requisicaoBloqueadaNaoChegaACadeia() throws Exception {
        call("POST", "/v1/auth/register", "1.1.1.1");
        assertThat(chainCalls.get()).isEqualTo(1);

        call("POST", "/v1/auth/register", "1.1.1.1");

        assertThat(chainCalls.get()).isEqualTo(1);
    }

    @Test
    void regraPorEndpointLimitaSoOEndpointConfigurado() throws Exception {
        assertThat(call("POST", "/v1/orders", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/v1/orders", "1.1.1.1").getStatus()).isEqualTo(429);

        // GET no mesmo caminho e outros caminhos usam o limite geral.
        assertThat(call("GET", "/v1/orders", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(call("GET", "/v1/products", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void loginELimiteGeralSaoIndependentes() throws Exception {
        call("POST", "/v1/auth/login", "1.1.1.1");
        call("POST", "/v1/auth/login", "1.1.1.1");
        assertThat(call("POST", "/v1/auth/login", "1.1.1.1").getStatus()).isEqualTo(429);

        assertThat(call("GET", "/v1/products", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void caminhoParecidoNaoCaiNoLimiteDeLogin() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("GET", "/x/v1/auth/login", "1.1.1.1").getStatus())
                    .isEqualTo(200);
        }
        // A 4ª estoura o limite GERAL (3), não o de login (2).
        assertThat(call("GET", "/v1/auth/loginx", "1.1.1.1").getStatus())
                .isEqualTo(429);
    }

    @Test
    void barraFinalContaComoLogin() throws Exception {
        call("POST", "/v1/auth/login", "1.1.1.1");
        call("POST", "/v1/auth/login/", "1.1.1.1");

        assertThat(call("POST", "/v1/auth/login//", "1.1.1.1").getStatus())
                .isEqualTo(429);
    }

    @Test
    void optionsNaoConsomeCota() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("OPTIONS", "/v1/products", "1.1.1.1").getStatus())
                    .isEqualTo(200);
        }

        for (int i = 0; i < 3; i++) {
            assertThat(call("GET", "/v1/products", "1.1.1.1").getStatus())
                    .isEqualTo(200);
        }
        assertThat(call("GET", "/v1/products", "1.1.1.1").getStatus()).isEqualTo(429);
    }

    @Test
    void ipsDistintosTemCotasIndependentes() throws Exception {
        call("POST", "/v1/auth/register", "1.1.1.1");

        assertThat(call("POST", "/v1/auth/register", "1.1.1.1").getStatus())
                .isEqualTo(429);
        assertThat(call("POST", "/v1/auth/register", "2.2.2.2").getStatus())
                .isEqualTo(200);
    }

    @Test
    void reabreDepoisDaJanela() throws Exception {
        call("POST", "/v1/auth/login", "1.1.1.1");
        call("POST", "/v1/auth/login", "1.1.1.1");
        assertThat(call("POST", "/v1/auth/login", "1.1.1.1").getStatus()).isEqualTo(429);

        time.addAndGet(60_000);

        assertThat(call("POST", "/v1/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
    }
}
