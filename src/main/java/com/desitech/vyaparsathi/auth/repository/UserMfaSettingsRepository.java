package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.UserMfaSettings;
import com.desitech.vyaparsathi.common.annotations.SkipShopFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MFA rows are user-scoped, not shop-scoped — a user with multi-shop
 * membership shares one MFA setup. {@link SkipShopFilter} bypasses the
 * global tenant filter for this repository.
 */
@SkipShopFilter
@Repository
public interface UserMfaSettingsRepository extends JpaRepository<UserMfaSettings, Long> {

    Optional<UserMfaSettings> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM UserMfaSettings s WHERE s.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
