package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.UserBackupCode;
import com.desitech.vyaparsathi.common.annotations.SkipShopFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@SkipShopFilter
@Repository
public interface UserBackupCodeRepository extends JpaRepository<UserBackupCode, Long> {

    Optional<UserBackupCode> findByUserIdAndCodeHashAndUsedFalse(Long userId, String codeHash);

    List<UserBackupCode> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndUsedFalse(Long userId);

    @Modifying
    @Query("DELETE FROM UserBackupCode c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
