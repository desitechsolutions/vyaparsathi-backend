package com.desitech.vyaparsathi.platform.service;

import com.desitech.vyaparsathi.auth.entity.ImpersonationSession;
import com.desitech.vyaparsathi.auth.repository.ImpersonationSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImpersonationSessionCacheManager {

    private final ImpersonationSessionRepository impersonationSessionRepository;
    private final Map<String, CacheEntry> sessionCache = new ConcurrentHashMap<>();

    public ValidationResult validateSession(String sessionUuid) {
        if (sessionUuid == null || sessionUuid.isBlank()) {
            log.warn("[SECURITY IMPERSONATION REJECTED] Reason=INVALID_UUID, sessionUuid=null");
            return ValidationResult.NOT_FOUND;
        }

        LocalDateTime now = LocalDateTime.now();
        CacheEntry entry = sessionCache.get(sessionUuid);

        // Check if cached entry exists and is fresh (e.g. within 15 seconds)
        if (entry != null && entry.cachedAt().plusSeconds(15).isAfter(now)) {
            if (entry.ended()) {
                log.warn("[SECURITY IMPERSONATION REJECTED] Reason=ENDED, sessionUuid={}", sessionUuid);
                return ValidationResult.ENDED;
            }
            if (entry.expiresAt().isBefore(now)) {
                log.warn("[SECURITY IMPERSONATION REJECTED] Reason=EXPIRED, sessionUuid={}", sessionUuid);
                return ValidationResult.EXPIRED;
            }
            return ValidationResult.VALID;
        }

        // Cache miss or expired TTL: check database
        Optional<ImpersonationSession> optionalSession = impersonationSessionRepository.findBySessionUuid(sessionUuid);
        if (optionalSession.isEmpty()) {
            sessionCache.put(sessionUuid, new CacheEntry(true, now.plusYears(1), now));
            log.warn("[SECURITY IMPERSONATION REJECTED] Reason=NOT_FOUND, sessionUuid={}", sessionUuid);
            return ValidationResult.NOT_FOUND;
        }

        ImpersonationSession session = optionalSession.get();
        if (session.getEndedAt() != null) {
            sessionCache.put(sessionUuid, new CacheEntry(true, session.getExpiresAt(), now));
            log.warn("[SECURITY IMPERSONATION REJECTED] Reason=ENDED, sessionUuid={}", sessionUuid);
            return ValidationResult.ENDED;
        }

        if (session.getExpiresAt().isBefore(now)) {
            sessionCache.put(sessionUuid, new CacheEntry(true, session.getExpiresAt(), now));
            log.warn("[SECURITY IMPERSONATION REJECTED] Reason=EXPIRED, sessionUuid={}", sessionUuid);
            return ValidationResult.EXPIRED;
        }

        // Valid session: cache it
        sessionCache.put(sessionUuid, new CacheEntry(false, session.getExpiresAt(), now));
        return ValidationResult.VALID;
    }

    public void evictSession(String sessionUuid) {
        if (sessionUuid != null) {
            // Instantly mark as ended in cache
            sessionCache.put(sessionUuid, new CacheEntry(true, LocalDateTime.now(), LocalDateTime.now()));
            log.info("[SECURITY IMPERSONATION EXITED] sessionUuid={}", sessionUuid);
        }
    }

    public enum ValidationResult {
        VALID,
        ENDED,
        EXPIRED,
        NOT_FOUND
    }

    private record CacheEntry(boolean ended, LocalDateTime expiresAt, LocalDateTime cachedAt) {}
}
