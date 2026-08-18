package com.desitech.vyaparsathi.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Single-use recovery code for MFA-enabled users. Ten are typically issued
 * at enrollment; each can be consumed once instead of a TOTP code.
 *
 * Only the SHA-256 hash of the code is persisted — the raw value is shown
 * to the user exactly once, at enrollment or on regeneration.
 */
@Entity
@Table(name = "user_backup_codes")
@Getter
@Setter
@NoArgsConstructor
public class UserBackupCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private boolean used = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
