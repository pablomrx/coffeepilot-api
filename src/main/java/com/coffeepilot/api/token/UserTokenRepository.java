package com.coffeepilot.api.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findFirstByUserIdAndTypeOrderByCreatedAtDesc(
            Long userId,
            TokenType type
    );

    Optional<UserToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update UserToken t
               set t.usedAt = :now
             where t.tokenHash = :hash
               and t.type = :type
               and t.usedAt is null
               and t.revokedAt is null
               and t.expiresAt > :now
            """)
    int consume(
            @Param("hash") String hash,
            @Param("type") TokenType type,
            @Param("now") OffsetDateTime now
    );

    @Modifying
    @Query("""
            update UserToken t
               set t.revokedAt = :now
             where t.userId = :userId
               and t.type = :type
               and t.usedAt is null
               and t.revokedAt is null
            """)
    int revokeActive(
            @Param("userId") Long userId,
            @Param("type") TokenType type,
            @Param("now") OffsetDateTime now
    );

    @Modifying
    @Query("""
            update UserToken t
               set t.revokedAt = :now
             where t.userId = :userId
               and t.usedAt is null
               and t.revokedAt is null
            """)
    int revokeAllActive(
            @Param("userId") Long userId,
            @Param("now") OffsetDateTime now
    );
}
