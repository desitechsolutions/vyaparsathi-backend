package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    @Query(value = "SELECT * FROM password_reset_tokens WHERE token_hash = :tokenHash", nativeQuery = true)
    Optional<PasswordResetToken> findByTokenHashUnfiltered(@Param("tokenHash") String tokenHash);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiryDate < :now OR t.used = :used")
    int deleteAllByExpiryDateBeforeOrUsed(LocalDateTime now, boolean used);
}
