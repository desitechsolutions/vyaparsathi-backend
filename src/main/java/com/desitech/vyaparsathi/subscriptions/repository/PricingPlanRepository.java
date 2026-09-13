package com.desitech.vyaparsathi.subscriptions.repository;

import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Pessimistic-write locked fetch used when resolving/creating the durable
     * Razorpay Plan-ID cache on {@code PricingPlanConfig}, so two concurrent
     * checkouts for a brand-new price point can't both create a duplicate
     * Razorpay {@code Plan} object.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PricingPlanConfig p WHERE p.tier = :tier")
    Optional<PricingPlanConfig> findByIdForUpdate(@Param("tier") Tier tier);
}