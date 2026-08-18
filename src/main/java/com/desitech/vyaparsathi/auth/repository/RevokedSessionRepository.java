package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.RevokedSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Note: extends JpaRepository directly (not BaseRepository) because
 * this table is not shop-scoped — the {@link
 * com.desitech.vyaparsathi.common.aspect.ShopFilterAspect} would
 * otherwise try to inject a shop_id WHERE clause on a table that
 * doesn't have one.
 */
public interface RevokedSessionRepository extends JpaRepository<RevokedSession, String> {

    List<RevokedSession> findByExpiresAtAfter(LocalDateTime cutoff);

    @Modifying
    @Query("DELETE FROM RevokedSession r WHERE r.expiresAt <= :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
