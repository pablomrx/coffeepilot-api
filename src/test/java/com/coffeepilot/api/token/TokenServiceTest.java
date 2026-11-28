package com.coffeepilot.api.token;

import com.coffeepilot.api.common.exception.InvalidTokenException;
import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-11-26T12:00:00Z");
    private static final Long USER_ID = 7L;
    private static final TokenType TYPE = TokenType.PASSWORD_RESET;
    private static final String SHA256_OF_ABC =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Mock
    private UserTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private User user;

    private TokenService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);
        service = new TokenService(tokenRepository, userRepository, clock);
    }

    private void userExists() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
    }

    private UserToken previousTokenCreatedAt(OffsetDateTime createdAt) {
        return new UserToken(
                USER_ID, TYPE, "a".repeat(64), createdAt, createdAt.plus(TYPE.validity()));
    }

    private void previousTokenExists(OffsetDateTime createdAt) {
        when(tokenRepository.findFirstByUserIdAndTypeOrderByCreatedAtDesc(USER_ID, TYPE))
                .thenReturn(Optional.of(previousTokenCreatedAt(createdAt)));
    }

    private UserToken savedToken() {
        ArgumentCaptor<UserToken> captor = ArgumentCaptor.forClass(UserToken.class);
        verify(tokenRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void hashIsSha256InLowercaseHex() {
        assertEquals(SHA256_OF_ABC, TokenService.hash("abc"));
        assertTrue(TokenService.hash("qualquer-coisa").matches("^[0-9a-f]{64}$"));
    }

    @Test
    void issueFailsForUnknownUser() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.issue(USER_ID, TYPE));

        verifyNoInteractions(tokenRepository);
    }

    @Test
    void issueLocksTheUserBeforeAnythingElse() {
        userExists();

        service.issue(USER_ID, TYPE);

        InOrder order = inOrder(userRepository, tokenRepository);
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(tokenRepository)
                .findFirstByUserIdAndTypeOrderByCreatedAtDesc(USER_ID, TYPE);
    }

    @Test
    void issueReturnsAUrlSafeTokenOf43Characters() {
        userExists();

        String token = service.issue(USER_ID, TYPE).orElseThrow();

        assertEquals(43, token.length());
        assertTrue(token.matches("^[A-Za-z0-9_-]{43}$"));
    }

    @Test
    void issueStoresOnlyTheHashOfTheToken() {
        userExists();

        String token = service.issue(USER_ID, TYPE).orElseThrow();

        UserToken saved = savedToken();
        assertEquals(TokenService.hash(token), saved.getTokenHash());
        assertNotEquals(token, saved.getTokenHash());
        assertFalse(saved.getTokenHash().contains(token));
    }

    @Test
    void issueSavesUserTypeCreationAndValidityOfTheType() {
        for (TokenType type : TokenType.values()) {
            org.mockito.Mockito.reset(tokenRepository);
            userExists();

            service.issue(USER_ID, type);

            UserToken saved = savedToken();
            assertEquals(USER_ID, saved.getUserId());
            assertEquals(type, saved.getType());
            assertEquals(NOW, saved.getCreatedAt());
            assertEquals(NOW.plus(type.validity()), saved.getExpiresAt());
        }
    }

    @Test
    void issueRevokesTheActiveTokenOfTheSameTypeBeforeSaving() {
        userExists();

        service.issue(USER_ID, TYPE);

        InOrder order = inOrder(tokenRepository);
        order.verify(tokenRepository).revokeActive(USER_ID, TYPE, NOW);
        order.verify(tokenRepository).save(any(UserToken.class));
    }

    @Test
    void issueGeneratesDifferentTokensEachTime() {
        userExists();

        String first = service.issue(USER_ID, TYPE).orElseThrow();
        String second = service.issue(USER_ID, TYPE).orElseThrow();

        assertNotEquals(first, second);
    }

    @Test
    void issueDoesNothingWhileTheCooldownIsActive() {
        userExists();
        previousTokenExists(NOW.minusSeconds(30));

        assertEquals(Optional.empty(), service.issue(USER_ID, TYPE));

        verify(tokenRepository, never()).revokeActive(any(), any(), any());
        verify(tokenRepository, never()).save(any(UserToken.class));
    }

    @Test
    void issueStillBlockedAtFiftyNineSeconds() {
        userExists();
        previousTokenExists(NOW.minusSeconds(59));

        assertEquals(Optional.empty(), service.issue(USER_ID, TYPE));
    }

    @Test
    void issueAllowedExactlyAtTheCooldownLimit() {
        userExists();
        previousTokenExists(NOW.minusSeconds(60));

        assertTrue(service.issue(USER_ID, TYPE).isPresent());
    }

    @Test
    void issueAllowedAfterTheCooldown() {
        userExists();
        previousTokenExists(NOW.minusSeconds(61));

        assertTrue(service.issue(USER_ID, TYPE).isPresent());
        verify(tokenRepository).revokeActive(USER_ID, TYPE, NOW);
    }

    @Test
    void consumeReturnsTheUserOfAValidToken() {
        String hash = TokenService.hash("abc");
        when(tokenRepository.consume(hash, TYPE, NOW)).thenReturn(1);
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(
                new UserToken(USER_ID, TYPE, hash, NOW.minusMinutes(5), NOW.plusMinutes(25))));

        assertEquals(USER_ID, service.consume("abc", TYPE));
    }

    @Test
    void consumeIgnoresSurroundingWhitespace() {
        String hash = TokenService.hash("abc");
        when(tokenRepository.consume(hash, TYPE, NOW)).thenReturn(1);
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(
                new UserToken(USER_ID, TYPE, hash, NOW.minusMinutes(5), NOW.plusMinutes(25))));

        assertEquals(USER_ID, service.consume("  abc \n", TYPE));
    }

    @Test
    void consumeRejectsATokenThatIsNotValidAnymore() {
        when(tokenRepository.consume(anyString(), eq(TYPE), eq(NOW))).thenReturn(0);

        assertThrows(InvalidTokenException.class,
                () -> service.consume("abc", TYPE));

        verify(tokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void consumeRejectsNullAndBlankWithoutTouchingTheDatabase() {
        assertThrows(InvalidTokenException.class, () -> service.consume(null, TYPE));
        assertThrows(InvalidTokenException.class, () -> service.consume("   ", TYPE));

        verifyNoInteractions(tokenRepository);
    }

    @Test
    void consumeFailsLoudlyIfTheConsumedTokenDisappears() {
        String hash = TokenService.hash("abc");
        when(tokenRepository.consume(hash, TYPE, NOW)).thenReturn(1);
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.consume("abc", TYPE));
    }

    @Test
    void revokeAllLocksTheUserBeforeRevokingEveryActiveToken() {
        userExists();

        service.revokeAll(USER_ID);

        InOrder order = inOrder(userRepository, tokenRepository);
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(tokenRepository).revokeAllActive(USER_ID, NOW);
    }

    @Test
    void revokeAllFailsForUnknownUserWithoutRevoking() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.revokeAll(USER_ID));

        verifyNoInteractions(tokenRepository);
    }
}
