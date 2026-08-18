package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.entity.RevokedSession;
import com.desitech.vyaparsathi.auth.repository.RevokedSessionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fast in-memory session denylist consulted by
 * {@code JwtAuthenticationFilter} on every request.
 *
 * <p>Entries live in a {@link ConcurrentHashMap#newKeySet()} so the
 * hot path is a lock-free O(1) check. The DB table {@code
 * revoked_sessions} is the durable backing store; on startup we hydrate
 * the in-memory set from all rows whose {@code expiresAt} is still
 * in the future, and a scheduled sweep clears out expired rows so the
 * table doesn't grow unbounded.</p>
 *
 * <p>Expiry is picked to match the access-token TTL — once no in-flight
 * access token could still carry the revoked session_id, the denylist
 * entry has done its job.</p>
 */
@Service
public class SessionDenylistService {

    private static final Logger logger = LoggerFactory.getLogger(SessionDenylistService.class);

    private final RevokedSessionRepository repository;
    private final Set<String> inMemory = ConcurrentHashMap.newKeySet();

    public SessionDenylistService(RevokedSessionRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void hydrateFromDb() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<RevokedSession> live = repository.findByExpiresAtAfter(now);
            for (RevokedSession r : live) {
                inMemory.add(r.getSessionId());
            }
            logger.info("Session denylist hydrated with {} live entries", inMemory.size());
        } catch (Exception e) {
            // Never let a hydration failure crash the app — fall back
            // to an empty in-memory set and rely on DB reads later.
            logger.warn("Session denylist hydration failed — starting empty ({})", e.getMessage());
        }
    }

    public boolean isRevoked(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return inMemory.contains(sessionId);
    }

    /**
     * REQUIRES_NEW so a revoke persisted from inside a larger auth
     * transaction (logout, password reset) survives even if the outer
     * transaction rolls back for an unrelated reason.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revoke(String sessionId, Long userId, LocalDateTime expiresAt, String reason) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        RevokedSession entry = repository.findById(sessionId).orElseGet(RevokedSession::new);
        entry.setSessionId(sessionId);
        entry.setUserId(userId);
        entry.setRevokedAt(LocalDateTime.now());
        entry.setExpiresAt(expiresAt);
        entry.setReason(reason);
        repository.save(entry);
        inMemory.add(sessionId);
        logger.info("Session {} added to denylist (reason={})", sessionId, reason);
    }

    /**
     * Scheduled sweep — every 15 minutes — removes entries whose
     * expires_at is in the past. Cheap because the table stays small
     * and we hit an index on expires_at.
     */
    @Scheduled(fixedDelayString = "${session.denylist.sweep-ms:900000}")
    @Transactional
    public void purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now();
        int removed = repository.deleteExpired(cutoff);
        // Re-hydrate the in-memory set from remaining rows so we don't
        // keep stale entries alive forever. Cheaper than tracking
        // expiry per-entry in memory.
        if (removed > 0) {
            inMemory.clear();
            for (RevokedSession r : repository.findByExpiresAtAfter(cutoff)) {
                inMemory.add(r.getSessionId());
            }
            logger.debug("Denylist sweep removed {} expired entries; {} remain live", removed, inMemory.size());
        }
    }

    /** Test hook — never call from application code. */
    void clearForTests() {
        inMemory.clear();
    }
}
