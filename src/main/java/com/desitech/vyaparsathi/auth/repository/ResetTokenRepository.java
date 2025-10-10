package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.ResetToken;
import com.desitech.vyaparsathi.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResetTokenRepository extends BaseRepository<ResetToken, Long> {
    Optional<ResetToken> findByToken(String token);
    void deleteByToken(String token);

    @Modifying
    @Query("DELETE FROM ResetToken t WHERE t.expiry < CURRENT_TIMESTAMP")
    void deleteAllExpiredTokens();
}