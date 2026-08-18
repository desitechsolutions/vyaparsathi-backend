package com.desitech.vyaparsathi.rbac.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Shop-scoped staff invitation. Mirrors {@code AdminInvitation}
 * (platform-level) but ties the invite to a specific shop and role.
 *
 * <p>Only the SHA-256 hash of the token is stored. The raw token is
 * emailed to the recipient and never persisted, matching the pattern
 * from {@code AdminInvitationService}.
 */
@Entity
@Table(name = "shop_invitations")
@Getter
@Setter
@NoArgsConstructor
public class ShopInvitation {

    public enum Status { PENDING, ACCEPTED, REVOKED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "role_name", nullable = false, length = 64)
    private String roleName;

    @Column(name = "invited_by", nullable = false)
    private Long invitedBy;

    @Column(name = "inviter_name", length = 200)
    private String inviterName;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(length = 500)
    private String message;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "accepted_by_user_id")
    private Long acceptedByUserId;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
}
