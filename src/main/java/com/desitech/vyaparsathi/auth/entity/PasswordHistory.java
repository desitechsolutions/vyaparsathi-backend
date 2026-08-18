package com.desitech.vyaparsathi.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Row-per-password-change ledger used to prevent password reuse.
 *
 * We keep only a fixed window of rows per user (see
 * {@code PasswordHistoryService.HISTORY_WINDOW}) so the table doesn't grow
 * unbounded. The current active password's hash also lives in
 * {@code users.password_hash} — the check compares against both this
 * table's window AND the current active hash.
 *
 * Not scoped to a shop: password identity is per-user, not per-shop.
 */
@Entity
@Table(name = "password_history")
@Getter
@Setter
@NoArgsConstructor
public class PasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
