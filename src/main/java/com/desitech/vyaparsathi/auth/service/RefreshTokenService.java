package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.auth.repository.RefreshTokenRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.exception.TokenExpiredException;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages refresh tokens — each row is one active user session
 * (browser/device). Multi-session by design: a user who signs in on
 * a laptop AND a phone gets two rows, both valid, listable and
 * revocable independently from the Active Sessions UI.
 *
 * <p>Device metadata (session_id, device_label, user_agent, ip,
 * last_active_at) is populated by callers via
 * {@link #createRefreshToken(String, SessionMetadata)}; the legacy
 * no-metadata form still exists for internal call sites (impersonation
 * exit) and generates a synthetic session so the row is well-formed.</p>
 */
@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);
    @Value("${app.jwtRefreshExpirationMs:604800000}") // 7 days default
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private ShopRepository shopRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Metadata about the session being opened — captured from the
     * inbound HTTP request so revoked-session listings can show the
     * user a meaningful "Chrome on macOS · 122.34.56.7 · 2m ago" row.
     */
    public static class SessionMetadata {
        private final String sessionId;
        private final String deviceLabel;
        private final String userAgent;
        private final String ipAddress;

        public SessionMetadata(String sessionId, String deviceLabel, String userAgent, String ipAddress) {
            this.sessionId = sessionId;
            this.deviceLabel = deviceLabel;
            this.userAgent = userAgent;
            this.ipAddress = ipAddress;
        }
        public String getSessionId() { return sessionId; }
        public String getDeviceLabel() { return deviceLabel; }
        public String getUserAgent() { return userAgent; }
        public String getIpAddress() { return ipAddress; }
    }

    /**
     * Legacy overload — kept for callers that don't have HTTP context
     * (background jobs, impersonation exit). Generates a synthetic
     * session so the row still has a public identifier.
     */
    @Transactional
    public RefreshToken createRefreshToken(String username) {
        return createRefreshToken(username, new SessionMetadata(newSessionId(), "Unknown device", null, null));
    }

    /**
     * Create a new session for the given username. Does NOT delete
     * other sessions for the same user — that would break multi-device
     * sign-in. Old sessions expire naturally or are revoked via the
     * Active Sessions UI.
     */
    @Transactional
    public RefreshToken createRefreshToken(String username, SessionMetadata metadata) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsername(username);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshToken.setSessionId(metadata.getSessionId() != null ? metadata.getSessionId() : newSessionId());
        refreshToken.setDeviceLabel(metadata.getDeviceLabel());
        refreshToken.setUserAgent(metadata.getUserAgent());
        refreshToken.setIpAddress(metadata.getIpAddress());
        refreshToken.setLastActiveAt(LocalDateTime.now());

        Long currentShopId = TenantContext.getCurrentShopId();
        if (currentShopId != null) {
            Shop shop = shopRepository.findById(currentShopId)
                    .orElseThrow(() -> new ApplicationException("Shop not found for id: " + currentShopId));
            refreshToken.setShop(shop);
            logger.debug("Refresh token created with shop id: {}", currentShopId);
        } else {
            logger.debug("Refresh token created without shop (normal for PENDING_OWNER / platform admin)");
        }

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Look up a token and enforce expiry/revocation semantics.
     * Returns the token if it's still valid; throws otherwise so the
     * refresh endpoint can respond with 401.
     */
    @Transactional
    public RefreshToken validateAndGet(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (refreshToken.getRevokedAt() != null) {
            // Revoked explicitly (user revoked from Active Sessions, or
            // logout). Delete the row so it's not retried.
            refreshTokenRepository.delete(refreshToken);
            throw new TokenExpiredException("Session has been revoked. Please sign in again.");
        }

        if (isExpired(refreshToken)) {
            refreshTokenRepository.delete(refreshToken);
            throw new TokenExpiredException("Refresh token expired. Please log in again.");
        }
        if (refreshToken.getShop() != null) {
            TenantContext.setCurrentShopId(refreshToken.getShop().getId());
        } else {
            TenantContext.clear();
        }
        return refreshToken;
    }

    public boolean isExpired(RefreshToken token) {
        return token.getExpiryDate().isBefore(Instant.now());
    }

    /**
     * Called by password change / password reset / disable-account
     * flows — invalidates every session so the user has to sign in
     * fresh everywhere.
     */
    @Transactional
    public void deleteByUsername(String username) {
        refreshTokenRepository.deleteByUsername(username);
    }

    @Transactional
    public void delete(RefreshToken token) {
        refreshTokenRepository.delete(token);
    }

    @Transactional
    public void deleteByToken(String token) {
        refreshTokenRepository.findByToken(token)
                .ifPresent(refreshTokenRepository::delete);
    }

    /**
     * Rotate on refresh: keep the same row (preserving session_id and
     * created_at for the Active Sessions UI) but replace the refresh
     * token value + push the expiry forward + refresh last-seen + ua/ip.
     *
     * <p>Reusing the existing row is important: session_id has a UNIQUE
     * index, so INSERT-then-DELETE would trip the constraint mid-tx.</p>
     */
    @Transactional
    public RefreshToken rotateWithSameSession(RefreshToken previous, SessionMetadata refreshedMetadata) {
        previous.setToken(UUID.randomUUID().toString());
        previous.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        if (refreshedMetadata != null) {
            if (refreshedMetadata.getUserAgent() != null) {
                previous.setUserAgent(refreshedMetadata.getUserAgent());
            }
            if (refreshedMetadata.getIpAddress() != null) {
                previous.setIpAddress(refreshedMetadata.getIpAddress());
            }
        }
        previous.setLastActiveAt(LocalDateTime.now());
        return refreshTokenRepository.save(previous);
    }

    // ─── Session-aware queries used by the Active Sessions UI ────────
    public Optional<RefreshToken> findBySessionId(String sessionId) {
        return refreshTokenRepository.findBySessionId(sessionId);
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public List<RefreshToken> findActiveByUsername(String username) {
        return refreshTokenRepository.findByUsernameAndRevokedAtIsNull(username);
    }

    /**
     * Mark a single session revoked without hard-deleting — kept in
     * the table so the Active Sessions UI can distinguish "expired"
     * from "revoked by you" and the audit trail is preserved.
     */
    @Transactional
    public void revoke(RefreshToken token, String reason) {
        if (token == null || token.getRevokedAt() != null) {
            return;
        }
        token.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);
    }

    private String newSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
