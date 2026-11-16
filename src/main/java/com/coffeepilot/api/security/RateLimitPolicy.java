package com.coffeepilot.api.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.LongSupplier;

public class RateLimitPolicy {

    public static final String LOGIN_PATH = "/v1/auth/login";
    public static final String REGISTER_PATH = "/v1/auth/register";

    static final int MAX_LOGIN_PER_MINUTE = 20;
    static final int MAX_REGISTER_PER_HOUR = 20;
    static final int MAX_REQUESTS_PER_RULE = 100_000;
    static final Duration MIN_WINDOW = Duration.ofSeconds(1);
    static final Duration MAX_WINDOW = Duration.ofDays(1);

    private static final Set<String> METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private record CompiledRule(
            String method,
            String path,
            boolean wildcard,
            RateLimiter limiter
    ) {
        boolean matches(String requestMethod, String requestPath) {
            if (method != null && !method.equals(requestMethod)) {
                return false;
            }

            if (!wildcard) {
                return path.equals(requestPath);
            }

            return requestPath.equals(path)
                    || requestPath.startsWith(path + "/");
        }
    }

    private final RateLimiter loginLimiter;
    private final RateLimiter registerLimiter;
    private final RateLimiter generalLimiter;
    private final List<CompiledRule> rules;

    public RateLimitPolicy(RateLimitProperties properties, LongSupplier clock) {
        int maxKeys = properties.maxKeys();

        requireRange("rate-limit.login-per-minute",
                properties.loginPerMinute(), MAX_LOGIN_PER_MINUTE);
        requireRange("rate-limit.register-per-hour",
                properties.registerPerHour(), MAX_REGISTER_PER_HOUR);
        requireRange("rate-limit.general-per-minute",
                properties.generalPerMinute(), MAX_REQUESTS_PER_RULE);

        this.loginLimiter = new RateLimiter(
                properties.loginPerMinute(), Duration.ofMinutes(1), maxKeys, clock);
        this.registerLimiter = new RateLimiter(
                properties.registerPerHour(), Duration.ofHours(1), maxKeys, clock);
        this.generalLimiter = new RateLimiter(
                properties.generalPerMinute(), Duration.ofMinutes(1), maxKeys, clock);

        this.rules = compile(properties.rules(), maxKeys, clock);
    }

    public RateLimiter resolve(String method, String path) {
        if (LOGIN_PATH.equals(path)) {
            return loginLimiter;
        }

        if (REGISTER_PATH.equals(path)) {
            return registerLimiter;
        }

        String upperMethod = method == null
                ? ""
                : method.toUpperCase(Locale.ROOT);

        for (CompiledRule rule : rules) {
            if (rule.matches(upperMethod, path)) {
                return rule.limiter();
            }
        }

        return generalLimiter;
    }

    public void evictExpired() {
        loginLimiter.evictExpired();
        registerLimiter.evictExpired();
        generalLimiter.evictExpired();
        rules.forEach(rule -> rule.limiter().evictExpired());
    }

    private static List<CompiledRule> compile(
            List<RateLimitProperties.Rule> source,
            int maxKeys,
            LongSupplier clock
    ) {
        List<CompiledRule> compiled = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < source.size(); i++) {
            RateLimitProperties.Rule rule = source.get(i);
            String where = "rate-limit.rules[" + i + "]";

            if (rule == null || rule.path() == null || rule.path().isBlank()) {
                throw new IllegalStateException(where + ": o caminho é obrigatório.");
            }

            String path = rule.path().trim();

            if (!path.startsWith("/v1/")) {
                throw new IllegalStateException(
                        where + ": o caminho deve começar com /v1/.");
            }

            if (path.contains("?") || path.contains(" ")) {
                throw new IllegalStateException(
                        where + ": o caminho não pode ter '?' nem espaços.");
            }

            boolean wildcard = path.endsWith("/**");
            String base = wildcard ? path.substring(0, path.length() - 3) : path;

            if (base.contains("*")) {
                throw new IllegalStateException(
                        where + ": curinga só é aceito no final, como /**.");
            }

            if (base.length() > 1 && base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }

            if (LOGIN_PATH.equals(base) || REGISTER_PATH.equals(base)) {
                throw new IllegalStateException(
                        where + ": login e register têm limite fixo e não podem ser configurados.");
            }

            String method = null;

            if (rule.method() != null && !rule.method().isBlank()) {
                method = rule.method().trim().toUpperCase(Locale.ROOT);

                if (!METHODS.contains(method)) {
                    throw new IllegalStateException(
                            where + ": método inválido (" + method + ").");
                }
            }

            requireRange(where + ".max", rule.max(), MAX_REQUESTS_PER_RULE);

            Duration window = rule.window();

            if (window == null
                    || window.compareTo(MIN_WINDOW) < 0
                    || window.compareTo(MAX_WINDOW) > 0) {
                throw new IllegalStateException(
                        where + ": a janela deve estar entre 1 segundo e 1 dia.");
            }

            String identity = (method == null ? "*" : method)
                    + " " + base + (wildcard ? "/**" : "");

            if (!seen.add(identity)) {
                throw new IllegalStateException(
                        where + ": regra duplicada (" + identity + ").");
            }

            compiled.add(new CompiledRule(
                    method,
                    base,
                    wildcard,
                    new RateLimiter(rule.max(), window, maxKeys, clock)));
        }

        compiled.sort(Comparator
                .comparing(CompiledRule::wildcard)
                .thenComparing(rule -> rule.method() == null)
                .thenComparing(rule -> -rule.path().length()));

        return List.copyOf(compiled);
    }

    private static void requireRange(String name, int value, int max) {
        if (value <= 0 || value > max) {
            throw new IllegalStateException(
                    name + " deve estar entre 1 e " + max + " (valor: " + value + ").");
        }
    }
}
