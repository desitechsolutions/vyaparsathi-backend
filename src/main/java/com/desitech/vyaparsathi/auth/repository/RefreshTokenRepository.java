package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.common.repository.BaseRepository;

import java.util.List;
import java.util.Optional;


public interface RefreshTokenRepository extends BaseRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findBySessionId(String sessionId);

    /**
     * All currently-active sessions for a username. "Active" = not
     * revoked. Callers filter expired rows in-memory (there aren't
     * many per user, and expiry check is easier than JPQL-comparing
     * Instant vs now).
     */
    List<RefreshToken> findByUsernameAndRevokedAtIsNull(String username);

    void deleteByUsername(String username);
}
