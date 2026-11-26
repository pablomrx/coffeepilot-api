package com.coffeepilot.api.auth;

import com.coffeepilot.api.establishment.Establishment;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String EMAIL = "ana@example.com";
    private static final String PASSWORD = "Senha@12345";
    private static final String DUMMY_HASH = "hash-descartavel";
    private static final String USER_HASH = "hash-real";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        // Usado pelo construtor, que gera o hash descartável.
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        service = new AuthenticationService(userRepository, passwordEncoder);
    }

    private static User user(Establishment establishment) {
        return new User(establishment, "Ana", EMAIL, USER_HASH, Role.OWNER);
    }

    private static User user() {
        return user(new Establishment("Cafeteria"));
    }

    private void given(User user) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private void passwordMatchesUserHash() {
        when(passwordEncoder.matches(PASSWORD, USER_HASH)).thenReturn(true);
    }

    // ---------- sucesso ----------

    @Test
    void correctCredentialsReturnTheUser() {
        User user = user();
        given(user);
        passwordMatchesUserHash();

        assertThat(service.authenticate(EMAIL, PASSWORD)).isSameAs(user);
    }

    @Test
    void emailIsNormalizedBeforeLookup() {
        User user = user();
        given(user);
        passwordMatchesUserHash();

        assertThat(service.authenticate("  ANA@Example.COM ", PASSWORD))
                .isSameAs(user);
    }

    @Test
    void emailNotConfirmedDoesNotFailAuthentication() {
        // A confirmação é verificada pelo LoginService, depois da senha.
        User user = user();
        given(user);
        passwordMatchesUserHash();

        assertThat(user.isEmailConfirmed()).isFalse();
        assertThat(service.authenticate(EMAIL, PASSWORD)).isSameAs(user);
    }

    // ---------- conta inexistente ----------

    @Test
    void unknownEmailIsInvalidAndStillComparesAgainstDummyHash() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD))
                .isInstanceOf(BadCredentialsException.class);

        verify(passwordEncoder).matches(PASSWORD, DUMMY_HASH);
    }

    @Test
    void unknownEmailNeverAuthenticatesEvenIfDummyHashMatches() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.matches(PASSWORD, DUMMY_HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ---------- convite pendente (hash nulo) ----------

    @Test
    void pendingInviteIsInvalidAndStillComparesAgainstDummyHash() {
        given(User.invited(new Establishment("Cafeteria"), "Bia", EMAIL,
                Role.ATTENDANT));

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciais inválidas.");

        verify(passwordEncoder).matches(PASSWORD, DUMMY_HASH);
    }

    @Test
    void pendingInviteNeverAuthenticatesEvenIfDummyHashMatches() {
        given(User.invited(new Establishment("Cafeteria"), "Bia", EMAIL,
                Role.ATTENDANT));
        when(passwordEncoder.matches(PASSWORD, DUMMY_HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ---------- senha errada ----------

    @Test
    void wrongPasswordIsInvalid() {
        given(user());
        when(passwordEncoder.matches(PASSWORD, USER_HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciais inválidas.");
    }

    // ---------- estado da conta ----------

    @Test
    void inactiveUserIsInvalidEvenWithCorrectPassword() {
        User user = user();
        user.setActive(false);
        given(user);
        passwordMatchesUserHash();

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD))
                .isInstanceOf(BadCredentialsException.class);

        verify(passwordEncoder).matches(PASSWORD, USER_HASH);
    }

    @Test
    void inactiveEstablishmentIsInvalidEvenWithCorrectPassword() {
        Establishment establishment = new Establishment("Cafeteria");
        establishment.setActive(false);
        given(user(establishment));
        passwordMatchesUserHash();

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD))
                .isInstanceOf(BadCredentialsException.class);

        verify(passwordEncoder).matches(PASSWORD, USER_HASH);
    }

    @Test
    void sandboxAccountNeverAuthenticatesByPassword() {
        Establishment demo = Establishment.demo(
                "Demo", Duration.ofMinutes(45), CLOCK);
        given(user(demo));
        passwordMatchesUserHash();

        assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD))
                .isInstanceOf(BadCredentialsException.class);

        // O custo do BCrypt é o mesmo de qualquer outra recusa.
        verify(passwordEncoder).matches(PASSWORD, USER_HASH);
    }

    // ---------- limites da entrada ----------

    @Test
    void passwordOver72BytesNeverReachesBcrypt() {
        String tooLong = "a".repeat(73);
        given(user());

        assertThatThrownBy(() -> service.authenticate(EMAIL, tooLong))
                .isInstanceOf(BadCredentialsException.class);

        verify(passwordEncoder, never()).matches(eq(tooLong), anyString());
        verify(passwordEncoder).matches(anyString(), eq(DUMMY_HASH));
    }

    @Test
    void nullInputIsInvalidWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> service.authenticate(null, PASSWORD))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.authenticate(EMAIL, null))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(userRepository);
    }
}
