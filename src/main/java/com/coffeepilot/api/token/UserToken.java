package com.coffeepilot.api.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

@Entity
@Table(name = "user_tokens")
public class UserToken {

    private static final Pattern HASH_FORMAT = Pattern.compile("^[0-9a-f]{64}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TokenType type;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected UserToken() {
    }

    public UserToken(
            Long userId,
            TokenType type,
            String tokenHash,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt
    ) {
        Objects.requireNonNull(userId, "O usuário é obrigatório.");
        Objects.requireNonNull(type, "O tipo é obrigatório.");
        Objects.requireNonNull(createdAt, "A data de criação é obrigatória.");
        Objects.requireNonNull(expiresAt, "A data de expiração é obrigatória.");

        if (tokenHash == null || !HASH_FORMAT.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException(
                    "O hash do token deve ter 64 caracteres hexadecimais minúsculos."
            );
        }

        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "A expiração deve ser posterior à criação."
            );
        }

        this.userId = userId;
        this.type = type;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public TokenType getType() {
        return type;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
