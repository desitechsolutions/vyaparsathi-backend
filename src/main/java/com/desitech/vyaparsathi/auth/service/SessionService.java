package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Central coordinator for the session lifecycle — creation on login,
 * touch on refresh, revoke on logout / user action / password change.
 * AuthController and the sessions-management controller are the only
 * intended callers.
 *
 * <p>Under the hood a "session" is a {@link RefreshToken} row plus a
 * denylist entry when revoked. This service is the seam that keeps
 * both in step so no other code has to remember to update both.</p>
 */
@Service
public class SessionService {

    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

    private final RefreshTokenService refreshTokenService;
    private final SessionDenylistService denylist;
    private final DeviceLabelParser deviceLabelParser;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public SessionService(RefreshTokenService refreshTokenService,
                          SessionDenylistService denylist,
                          DeviceLabelParser deviceLabelParser,
                          JwtUtil jwtUtil,
                          UserRepository userRepository) {
        this.refreshTokenService = refreshTokenService;
        this.denylist = denylist;
        this.deviceLabelParser = deviceLabelParser;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    /**
     * Build a metadata bundle from the inbound HTTP request. Callers
     * pre-generate the session_id here so it can be woven into the
     * access token before the refresh row is even persisted.
     */
    public RefreshTokenService.SessionMetadata newSessionMetadata(HttpServletRequest request) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String deviceLabel = deviceLabelParser.parseUserAgent(request);
        String ua = deviceLabelParser.rawUserAgent(request);
        String ip = deviceLabelParser.extractClientIp(request);
        return new RefreshTokenService.SessionMetadata(sessionId, deviceLabel, ua, ip);
    }

    /**
     * Called from the refresh endpoint: preserve the original
     * session_id but update UA/IP if the client moved networks.
     */
    public RefreshTokenService.SessionMetadata refreshedMetadata(HttpServletRequest request, String preserveSessionId) {
        String deviceLabel = deviceLabelParser.parseUserAgent(request);
        String ua = deviceLabelParser.rawUserAgent(request);
        String ip = deviceLabelParser.extractClientIp(request);
        return new RefreshTokenService.SessionMetadata(preserveSessionId, deviceLabel, ua, ip);
    }

    /**
     * Revoke a single session by its public session_id — used from
     * the Active Sessions UI. Ownership is enforced: a caller can
     * only revoke sessions owned by their own username. The active
     * access token for the session is also denylisted so revocation
     * takes effect within milliseconds instead of waiting for TTL.
     */
    @Transactional
    public void revokeSession(String callerUsername, String sessionId, String reason) {
        RefreshToken row = refreshTokenService.findBySessionId(sessionId)
                .orElseThrow(() -> new ApplicationException("Session not found"));
        if (!Objects.equals(row.getUsername(), callerUsername)) {
            throw new ApplicationException("You can only revoke your own sessions.");
        }
        revokeInternal(row, reason);
    }

    /**
     * Revoke every session for the caller except the one they're
     * currently signed in with — the "Sign out everywhere else"
     * button. Silently no-ops when no other sessions exist.
     */
    @Transactional
    public int revokeAllExceptCurrent(String callerUsername, String currentSessionId, String reason) {
        List<RefreshToken> rows = refreshTokenService.findActiveByUsername(callerUsername);
        int count = 0;
        for (RefreshToken row : rows) {
            if (Objects.equals(row.getSessionId(), currentSessionId)) {
                continue;
            }
            revokeInternal(row, reason);
            count++;
        }
        return count;
    }

    /**
     * Revoke every session for the given user — used from password
     * change, password reset, admin deactivate. Skips no session.
     */
    @Transactional
    public void revokeAllForUser(String username, String reason) {
        List<RefreshToken> rows = refreshTokenService.findActiveByUsername(username);
        for (RefreshToken row : rows) {
            revokeInternal(row, reason);
        }
    }

    private void revokeInternal(RefreshToken row, String reason) {
        refreshTokenService.revoke(row, reason);
        Long userId = null;
        try {
            userId = userRepository.findByUsername(row.getUsername())
                    .map(User::getId).orElse(null);
        } catch (Exception ignored) {
            // Best-effort — not fatal if we can't find the user id.
        }
        LocalDateTime expiresAt = LocalDateTime.now()
                // Session-id denylist entry only has to outlive the
                // longest-possible in-flight access token from this
                // session (JWT TTL). A small safety cushion (60s)
                // covers clock drift between server and clients.
                .plusSeconds((jwtUtil.getAccessTokenTtlMillis() / 1000L) + 60);
        denylist.revoke(row.getSessionId(), userId, expiresAt, reason);
        logger.info("Revoked session {} for user {} (reason={})", row.getSessionId(), row.getUsername(), reason);
    }

    public List<RefreshToken> listActiveSessionsFor(String username) {
        return refreshTokenService.findActiveByUsername(username);
    }
}
