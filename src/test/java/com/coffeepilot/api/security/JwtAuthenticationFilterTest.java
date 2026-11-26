package com.coffeepilot.api.security;

import com.coffeepilot.api.establishment.Establishment;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
    );
    private static final Instant START = Instant.parse("2026-11-21T12:00:00Z");
    private static final long USER_ID = 10L;

    private final UserRepository userRepository = mock(UserRepository.class);

    private Clock clock;
    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        clock = Clock.fixed(START, ZoneOffset.UTC);
        jwtService = new JwtService(SECRET, 28800, clock);
        filter = new JwtAuthenticationFilter(jwtService, userRepository, clock);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static User unconfirmedUser(Establishment establishment) {
        ReflectionTestUtils.setField(establishment, "id", 1L);

        User user = new User(
                establishment, "Ana", "ana@example.com", "hash", Role.OWNER
        );
        ReflectionTestUtils.setField(user, "id", USER_ID);

        return user;
    }

    private static User user(Establishment establishment) {
        User user = unconfirmedUser(establishment);
        user.confirmEmail(OffsetDateTime.parse("2026-11-21T12:00:00Z"));

        return user;
    }

    private static User realUser() {
        return user(new Establishment("Cafeteria"));
    }

    private void run(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }

        filter.doFilter(
                request, new MockHttpServletResponse(), new MockFilterChain()
        );
    }

    private void runWithToken(User tokenOwner, User databaseUser) throws Exception {
        String token = jwtService.generateToken(tokenOwner);
        when(userRepository.findWithEstablishmentById(USER_ID))
                .thenReturn(Optional.of(databaseUser));

        run("Bearer " + token);
    }

    private static Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void authenticatesValidToken() throws Exception {
        User user = realUser();

        runWithToken(user, user);

        Authentication authentication = authentication();
        assertNotNull(authentication);
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER")));
    }

    private static void putPreviousAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anterior", null, List.of())
        );
    }

    @Test
    void clearsPreviousAuthenticationWhenTokenIsInvalid() throws Exception {
        putPreviousAuthentication();

        run("Bearer isto-nao-e-um-jwt");

        assertNull(authentication());
    }

    @Test
    void clearsPreviousAuthenticationWhenUserIsRejected() throws Exception {
        User tokenOwner = realUser();
        User databaseUser = realUser();
        databaseUser.setActive(false);
        putPreviousAuthentication();

        runWithToken(tokenOwner, databaseUser);

        assertNull(authentication());
    }

    @Test
    void replacesPreviousAuthenticationWithTheCurrentUser() throws Exception {
        User user = realUser();
        putPreviousAuthentication();

        runWithToken(user, user);

        Authentication authentication = authentication();
        assertNotNull(authentication);
        assertTrue(!"anterior".equals(authentication.getPrincipal()));
    }

    @Test
    void ignoresRequestWithoutAuthorizationHeader() throws Exception {
        run(null);

        assertNull(authentication());
        verifyNoInteractions(userRepository);
    }

    @Test
    void ignoresInvalidToken() throws Exception {
        run("Bearer isto-nao-e-um-jwt");

        assertNull(authentication());
        verifyNoInteractions(userRepository);
    }

    @Test
    void ignoresTokenOfUnknownUser() throws Exception {
        String token = jwtService.generateToken(realUser());
        when(userRepository.findWithEstablishmentById(USER_ID))
                .thenReturn(Optional.empty());

        run("Bearer " + token);

        assertNull(authentication());
    }

    @Test
    void rejectsInactiveUser() throws Exception {
        User tokenOwner = realUser();
        User databaseUser = realUser();
        databaseUser.setActive(false);

        // O token foi emitido antes da desativação, com a versão antiga.
        runWithToken(tokenOwner, databaseUser);

        assertNull(authentication());
    }

    @Test
    void rejectsInactiveEstablishment() throws Exception {
        User tokenOwner = realUser();
        User databaseUser = realUser();
        databaseUser.getEstablishment().setActive(false);

        runWithToken(tokenOwner, databaseUser);

        assertNull(authentication());
    }

    @Test
    void rejectsUserWithUnconfirmedEmail() throws Exception {
        User user = unconfirmedUser(new Establishment("Cafeteria"));

        runWithToken(user, user);

        assertNull(authentication());
    }

    @Test
    void rejectsTokenAfterSessionsAreInvalidated() throws Exception {
        User user = realUser();
        String token = jwtService.generateToken(user);
        user.invalidateSessions();
        when(userRepository.findWithEstablishmentById(USER_ID))
                .thenReturn(Optional.of(user));

        run("Bearer " + token);

        assertNull(authentication());
    }

    @Test
    void rejectsTokenAfterDeactivationEvenIfReactivated() throws Exception {
        User user = realUser();
        String token = jwtService.generateToken(user);
        user.setActive(false);
        user.setActive(true);
        when(userRepository.findWithEstablishmentById(USER_ID))
                .thenReturn(Optional.of(user));

        run("Bearer " + token);

        assertNull(authentication());
    }

    @Test
    void rejectsTokenAfterRoleChange() throws Exception {
        User user = realUser();
        String token = jwtService.generateToken(user);
        user.setRole(Role.MANAGER);
        when(userRepository.findWithEstablishmentById(USER_ID))
                .thenReturn(Optional.of(user));

        run("Bearer " + token);

        assertNull(authentication());
    }

    @Test
    void rejectsUserOfExpiredSandbox() throws Exception {
        // O token é emitido para um usuário comum, com validade longa, para
        // que a recusa venha da regra de expiração da sandbox e não do JWT.
        User tokenOwner = realUser();
        Establishment demo = Establishment.demo(
                "Demo", Duration.ofMinutes(45), clock
        );
        User databaseUser = user(demo);
        String token = jwtService.generateToken(tokenOwner);

        Clock later = Clock.fixed(
                START.plus(Duration.ofMinutes(46)), ZoneOffset.UTC
        );
        JwtAuthenticationFilter laterFilter = new JwtAuthenticationFilter(
                new JwtService(SECRET, 28800, later), userRepository, later
        );
        when(userRepository.findWithEstablishmentById(USER_ID))
                .thenReturn(Optional.of(databaseUser));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        laterFilter.doFilter(
                request, new MockHttpServletResponse(), new MockFilterChain()
        );

        assertNull(authentication());
    }

    @Test
    void authenticatesUserOfActiveSandbox() throws Exception {
        Establishment demo = Establishment.demo(
                "Demo", Duration.ofMinutes(45), clock
        );
        User user = user(demo);

        runWithToken(user, user);

        assertNotNull(authentication());
    }
}
