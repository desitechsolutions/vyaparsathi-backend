package com.desitech.vyaparsathi.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Per-user MFA state. One row per {@link User} at most (enforced by the
 * unique index on user_id).
 *
 * <p>Not shop-scoped — MFA belongs to the identity, not the tenant. When a
 * user with multiple shop memberships (Phase 5) picks a shop, their MFA
 * setup is the same across all of them.
 *
 * <p>The {@code secret} is a Base32-encoded TOTP secret. It's sensitive but
 * useless without the user's authenticator device; we treat it like a
 * password hash and keep it out of DTOs.
 */
@Entity
@Table(name = "user_mfa_settings")
@Getter
@Setter
@NoArgsConstructor
public class UserMfaSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning user. Marked {@code updatable=false} because reparenting an
     * MFA row across users would be a security incident, not a normal
     * update.
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** Base32-encoded TOTP secret (typically 32 chars). */
    @Column(nullable = false, length = 255)
    private String secret;

    /**
     * True only AFTER the user has confirmed the initial code from their
     * authenticator app. The secret exists during enrollment; enabled
     * flips true once verified.
     */
    @Column(nullable = false)
    private boolean enabled = false;

    /** When the user confirmed the setup code that flipped {@code enabled} true. */
    @Column(name = "enrolled_at")
    private LocalDateTime enrolledAt;

    /** Rolling timestamp of the last successful TOTP verification. */
    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
