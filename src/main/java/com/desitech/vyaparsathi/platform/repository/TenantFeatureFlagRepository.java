package com.desitech.vyaparsathi.platform.repository;

import com.desitech.vyaparsathi.platform.entity.TenantFeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantFeatureFlagRepository extends JpaRepository<TenantFeatureFlag, Long> {
    Optional<TenantFeatureFlag> findByShopIdAndFeatureKey(Long shopId, String featureKey);
    List<TenantFeatureFlag> findByShopId(Long shopId);
}
