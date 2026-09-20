package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.common.annotations.SkipShopFilter;
import com.desitech.vyaparsathi.common.repository.BaseRepository;

import java.util.List;
import java.util.Optional;

/**
 * Auth infrastructure repository — intentionally excluded from the shop-filter aspect.
 *
 * <p>RefreshTokens are user-session artefacts, not shop-scoped tenant data.
 * A freshly registered OAuth2 user (no shop yet) must be able to receive a
 * refresh token as part of the login flow. {@code @SkipShopFilter} prevents
 * {@link com.desitech.vyaparsathi.common.aspect.ShopFilterAspect} from
 * rejecting the {@code .save()} call when {@code TenantContext.getCurrentShopId()}
 * is {@code null}.
 */
@SkipShopFilter
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
