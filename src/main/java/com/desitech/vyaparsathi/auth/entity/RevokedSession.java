package com.desitech.vyaparsathi.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Denylist entry consulted by {@link com.desitech.vyaparsathi.auth.security.JwtAuthenticationFilter}
 * on every authenticated request. When a session is revoked (explicit
 * user action, password reset, logout, admin kick), we store the
 * session_id here with an {@code expiresAt} set to the last possible
 * moment an access token from that session could still be valid.
 * A scheduled sweep drops rows past that time.
 *
 * <p>Not shop-scoped — denylist checks happen before tenant context
 * is applied, and platform-admin sessions have no shop.</p>
 */
@Entity
@Table(name = "revoked_sessions")
@Getter
@Setter
@NoArgsConstructor
public class RevokedSession {

    @Id
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "revoked_at", nullable = false)
    private LocalDateTime revokedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "reason", length = 64)
    private String reason;
}
