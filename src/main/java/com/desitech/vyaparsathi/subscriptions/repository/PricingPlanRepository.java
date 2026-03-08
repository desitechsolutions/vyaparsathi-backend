package com.desitech.vyaparsathi.subscriptions.repository;

import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PricingPlanRepository extends JpaRepository<PricingPlanConfig, Tier> {

    @Query("SELECT DISTINCT p FROM PricingPlanConfig p " +
            "LEFT JOIN FETCH p.features " +
            "WHERE p.isActive = true " +
            "ORDER BY p.sortOrder ASC")
    List<PricingPlanConfig> findAllActiveWithFeatures();
    // Used for Admin updates
    @Query("SELECT p FROM PricingPlanConfig p LEFT JOIN FETCH p.features WHERE p.tier = :tier")
    Optional<PricingPlanConfig> findByTierWithFeatures(Tier tier);
}