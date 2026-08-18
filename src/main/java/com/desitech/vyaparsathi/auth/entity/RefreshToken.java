package com.desitech.vyaparsathi.auth.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.shop.entity.Shop;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A refresh_token row is the canonical record of one active user
 * session — one entry per browser/device. Access tokens minted from
 * this session carry the {@link #sessionId} as their {@code sid}
 * claim, so a session revoke immediately kills every in-flight
 * access token issued by that session via the denylist check in
 * {@code JwtAuthenticationFilter}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken extends ShopAwareEntity {

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private Instant expiryDate;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private boolean revoked;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = true)
    private Shop shop;

    /**
     * Public identifier for this session — surfaced to the user in
     * the Active Sessions UI and used as the {@code sid} claim in
     * access tokens. UUID-shaped (no dashes) so it fits in a JWT
     * claim compactly.
     */
    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    /**
     * Human-readable "Chrome on macOS" style label parsed from the
     * User-Agent at session creation. Best-effort — may be null when
     * the UA header is missing or unrecognized.
     */
    @Column(name = "device_label", length = 255)
    private String deviceLabel;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    /**
     * Set when a user explicitly revokes a session (or on logout).
     * A row with revokedAt != null is filtered out of listings and
     * cannot be refreshed. Kept for audit rather than hard-deleted
     * so the sessions history remains inspectable.
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
