package com.coffeepilot.api.user;

import com.coffeepilot.api.establishment.Establishment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false, updatable = false)
    private Establishment establishment;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "email_confirmed_at")
    private OffsetDateTime emailConfirmedAt;

    @Column(name = "password_changed_at")
    private OffsetDateTime passwordChangedAt;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected User() {
    }

    public User(
            Establishment establishment,
            String name,
            String email,
            String passwordHash,
            Role role
    ) {
        this.establishment = establishment;
        this.name = name;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public static User invited(
            Establishment establishment,
            String name,
            String email,
            Role role
    ) {
        return new User(establishment, name, email, null, role);
    }

    public static User confirmed(
            Establishment establishment,
            String name,
            String email,
            String passwordHash,
            Role role,
            OffsetDateTime now
    ) {
        Objects.requireNonNull(now, "O instante é obrigatório.");

        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Conta confirmada exige senha."
            );
        }

        User user = new User(establishment, name, email, passwordHash, role);
        user.emailConfirmedAt = now;

        return user;
    }

    public static String normalizeEmail(String email) {
        return email == null
                ? null
                : email.trim().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public Establishment getEstablishment() {
        return establishment;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public void definePassword(String passwordHash, OffsetDateTime now) {
        Objects.requireNonNull(now, "O instante é obrigatório.");

        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "O hash da senha é obrigatório."
            );
        }

        this.passwordHash = passwordHash;
        this.passwordChangedAt = now;
        this.tokenVersion++;
    }

    public boolean isEmailConfirmed() {
        return emailConfirmedAt != null;
    }

    public OffsetDateTime getEmailConfirmedAt() {
        return emailConfirmedAt;
    }

    public void confirmEmail(OffsetDateTime now) {
        Objects.requireNonNull(now, "O instante é obrigatório.");

        if (!hasPassword()) {
            throw new IllegalStateException(
                    "Não é possível confirmar o e-mail de uma conta sem senha."
            );
        }

        if (emailConfirmedAt == null) {
            this.emailConfirmedAt = now;
        }
    }

    public void acceptInvite(String passwordHash, OffsetDateTime now) {
        Objects.requireNonNull(now, "O instante é obrigatório.");

        if (!isInvitePending()) {
            throw new IllegalStateException(
                    "Esta conta não possui convite pendente."
            );
        }

        definePassword(passwordHash, now);
        confirmEmail(now);
    }

    public boolean isInvitePending() {
        return !hasPassword() && !isEmailConfirmed();
    }

    public OffsetDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void invalidateSessions() {
        this.tokenVersion++;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        if (this.role != null && this.role != role) {
            this.tokenVersion++;
        }

        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (this.active && !active) {
            this.tokenVersion++;
        }

        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
