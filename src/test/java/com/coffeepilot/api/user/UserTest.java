package com.coffeepilot.api.user;

import com.coffeepilot.api.establishment.Establishment;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-11-21T12:00:00Z");

    private static Establishment establishment() {
        return new Establishment("Cafeteria");
    }

    private static User owner() {
        return new User(establishment(), "Ana", "ana@example.com", "hash", Role.OWNER);
    }

    private static User invited() {
        return User.invited(establishment(), "Bia", "bia@example.com", Role.ATTENDANT);
    }

    private static User confirmedUser() {
        return User.confirmed(
                establishment(), "Caio", "caio@example.com", "hash", Role.MANAGER, NOW
        );
    }

    // ---------- criação ----------

    @Test
    void constructorNormalizesEmail() {
        User user = new User(
                establishment(), "Ana", "  Ana@Example.COM ", "hash", Role.OWNER
        );

        assertEquals("ana@example.com", user.getEmail());
    }

    @Test
    void registeredOwnerHasPasswordButIsNotConfirmed() {
        User user = owner();

        assertTrue(user.hasPassword());
        assertFalse(user.isEmailConfirmed());
        assertFalse(user.isInvitePending());
        assertEquals(0, user.getTokenVersion());
    }

    @Test
    void invitedUserHasNoPasswordAndIsPending() {
        User user = invited();

        assertFalse(user.hasPassword());
        assertNull(user.getPasswordHash());
        assertFalse(user.isEmailConfirmed());
        assertTrue(user.isInvitePending());
    }

    @Test
    void confirmedFactoryCreatesConfirmedAccountWithPassword() {
        User user = confirmedUser();

        assertTrue(user.hasPassword());
        assertTrue(user.isEmailConfirmed());
        assertEquals(NOW, user.getEmailConfirmedAt());
        assertFalse(user.isInvitePending());
    }

    @Test
    void confirmedFactoryRejectsMissingPassword() {
        assertThrows(IllegalArgumentException.class, () -> User.confirmed(
                establishment(), "Caio", "caio@example.com", null, Role.MANAGER, NOW));
        assertThrows(IllegalArgumentException.class, () -> User.confirmed(
                establishment(), "Caio", "caio@example.com", "  ", Role.MANAGER, NOW));
    }

    @Test
    void confirmedFactoryRejectsNullInstant() {
        assertThrows(NullPointerException.class, () -> User.confirmed(
                establishment(), "Caio", "caio@example.com", "hash", Role.MANAGER, null));
    }

    // ---------- senha ----------

    @Test
    void definePasswordStoresHashRecordsDateAndInvalidatesSessions() {
        User user = owner();

        user.definePassword("novo-hash", NOW);

        assertEquals("novo-hash", user.getPasswordHash());
        assertEquals(NOW, user.getPasswordChangedAt());
        assertEquals(1, user.getTokenVersion());
    }

    @Test
    void definePasswordRejectsBlankHash() {
        User user = owner();

        assertThrows(IllegalArgumentException.class,
                () -> user.definePassword(null, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> user.definePassword("  ", NOW));
        assertEquals(0, user.getTokenVersion());
    }

    @Test
    void definePasswordRejectsNullInstant() {
        User user = owner();

        assertThrows(NullPointerException.class,
                () -> user.definePassword("novo-hash", null));
        assertEquals("hash", user.getPasswordHash());
    }

    // ---------- confirmação de e-mail ----------

    @Test
    void confirmEmailMarksAccountAsConfirmed() {
        User user = owner();

        user.confirmEmail(NOW);

        assertTrue(user.isEmailConfirmed());
        assertEquals(NOW, user.getEmailConfirmedAt());
    }

    @Test
    void confirmEmailKeepsTheFirstConfirmationDate() {
        User user = owner();
        user.confirmEmail(NOW);

        user.confirmEmail(NOW.plusDays(1));

        assertEquals(NOW, user.getEmailConfirmedAt());
    }

    @Test
    void confirmEmailRejectsAccountWithoutPassword() {
        User user = invited();

        assertThrows(IllegalStateException.class, () -> user.confirmEmail(NOW));
        assertFalse(user.isEmailConfirmed());
    }

    @Test
    void confirmEmailRejectsNullInstant() {
        User user = owner();

        assertThrows(NullPointerException.class, () -> user.confirmEmail(null));
        assertFalse(user.isEmailConfirmed());
    }

    // ---------- convite ----------

    @Test
    void acceptInviteDefinesPasswordAndConfirmsEmail() {
        User user = invited();

        user.acceptInvite("hash-escolhido", NOW);

        assertTrue(user.hasPassword());
        assertEquals("hash-escolhido", user.getPasswordHash());
        assertTrue(user.isEmailConfirmed());
        assertFalse(user.isInvitePending());
        assertEquals(NOW, user.getPasswordChangedAt());
        assertEquals(1, user.getTokenVersion());
    }

    @Test
    void acceptInviteRejectsAccountThatAlreadyHasPassword() {
        // Dono recém-cadastrado: tem senha, mas ainda não confirmou o e-mail.
        User user = owner();

        assertThrows(IllegalStateException.class,
                () -> user.acceptInvite("outro-hash", NOW));
        assertEquals("hash", user.getPasswordHash());
        assertEquals(0, user.getTokenVersion());
    }

    @Test
    void acceptInviteRejectsAlreadyConfirmedAccount() {
        User user = confirmedUser();

        assertThrows(IllegalStateException.class,
                () -> user.acceptInvite("outro-hash", NOW));
        assertEquals("hash", user.getPasswordHash());
    }

    @Test
    void acceptInviteCannotBeUsedTwice() {
        User user = invited();
        user.acceptInvite("primeiro", NOW);

        assertThrows(IllegalStateException.class,
                () -> user.acceptInvite("segundo", NOW));
        assertEquals("primeiro", user.getPasswordHash());
    }

    @Test
    void acceptInviteRejectsNullInstantWithoutChangingState() {
        User user = invited();

        assertThrows(NullPointerException.class,
                () -> user.acceptInvite("hash-escolhido", null));
        assertTrue(user.isInvitePending());
        assertEquals(0, user.getTokenVersion());
    }

    // ---------- sessões ----------

    @Test
    void invalidateSessionsIncrementsTokenVersion() {
        User user = owner();

        user.invalidateSessions();
        user.invalidateSessions();

        assertEquals(2, user.getTokenVersion());
    }

    @Test
    void deactivatingInvalidatesSessionsOnce() {
        User user = owner();

        user.setActive(false);
        user.setActive(false);

        assertFalse(user.isActive());
        assertEquals(1, user.getTokenVersion());
    }

    @Test
    void reactivatingDoesNotChangeTokenVersion() {
        User user = owner();
        user.setActive(false);

        user.setActive(true);

        assertTrue(user.isActive());
        assertEquals(1, user.getTokenVersion());
    }

    @Test
    void changingRoleInvalidatesSessions() {
        User user = owner();

        user.setRole(Role.MANAGER);

        assertEquals(Role.MANAGER, user.getRole());
        assertEquals(1, user.getTokenVersion());
    }

    @Test
    void settingTheSameRoleKeepsTokenVersion() {
        User user = owner();

        user.setRole(Role.OWNER);

        assertEquals(0, user.getTokenVersion());
    }
}
