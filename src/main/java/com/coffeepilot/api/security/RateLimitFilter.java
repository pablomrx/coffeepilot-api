package com.coffeepilot.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitPolicy policy;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitPolicy policy, ObjectMapper objectMapper) {
        this.policy = policy;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String path = normalizedPath(request);

        RateLimiter.Result result = policy
                .resolve(request.getMethod(), path)
                .tryAcquire(request.getRemoteAddr());

        if (!result.allowed()) {
            reject(response, path, result.retryAfterSeconds());
            return;
        }

        chain.doFilter(request, response);
    }

    private static String normalizedPath(HttpServletRequest request) {
        String path = request.getServletPath();

        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }

        int end = path.length();

        while (end > 1 && path.charAt(end - 1) == '/') {
            end--;
        }

        return path.substring(0, end);
    }

    private void reject(
            HttpServletResponse response,
            String path,
            long retryAfterSeconds
    ) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 429);
        body.put("error", "Too Many Requests");
        body.put("message", "Muitas requisições. Tente novamente em instantes.");
        body.put("path", path);
        body.put("fieldErrors", List.of());

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
