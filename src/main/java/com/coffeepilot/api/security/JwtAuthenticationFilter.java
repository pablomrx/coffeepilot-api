package com.coffeepilot.api.security;

import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final Clock clock;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            Clock clock
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            authenticate(header.substring(BEARER_PREFIX.length()).trim());
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        SecurityContextHolder.clearContext();

        Optional<JwtService.TokenClaims> claims = jwtService.parse(token);

        if (claims.isEmpty()) {
            return;
        }

        Optional<User> userOptional =
                userRepository.findWithEstablishmentById(claims.get().userId());

        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (!user.isActive() || !user.getEstablishment().isUsable(now)) {
            return;
        }

        if (user.getTokenVersion() != claims.get().tokenVersion()) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        AuthenticatedUser.from(user),
                        null,
                        List.of(new SimpleGrantedAuthority(
                                "ROLE_" + user.getRole().name()
                        ))
                );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
