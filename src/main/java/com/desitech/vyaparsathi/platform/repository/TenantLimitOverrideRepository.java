package com.desitech.vyaparsathi.platform.repository;

import com.desitech.vyaparsathi.platform.entity.TenantLimitOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantLimitOverrideRepository extends JpaRepository<TenantLimitOverride, Long> {

    @Query("SELECT t FROM TenantLimitOverride t WHERE t.shopId = :shopId AND t.resourceKey = :resourceKey AND t.isActive = true AND t.startDate <= :now AND (t.endDate IS NULL OR t.endDate >= :now) ORDER BY t.createdAt DESC")
    List<TenantLimitOverride> findActiveOverrides(@Param("shopId") Long shopId, @Param("resourceKey") String resourceKey, @Param("now") LocalDateTime now);

    List<TenantLimitOverride> findByShopIdAndIsActiveTrue(Long shopId);
}
