package com.desitech.vyaparsathi.platform.service;

import com.desitech.vyaparsathi.audit.entity.AuditLog;
import com.desitech.vyaparsathi.audit.repository.AuditLogRepository;
import com.desitech.vyaparsathi.platform.entity.PlatformFeatureFlag;
import com.desitech.vyaparsathi.platform.entity.TenantFeatureFlag;
import com.desitech.vyaparsathi.platform.entity.TenantLimitOverride;
import com.desitech.vyaparsathi.platform.repository.PlatformFeatureFlagRepository;
import com.desitech.vyaparsathi.platform.repository.TenantFeatureFlagRepository;
import com.desitech.vyaparsathi.platform.repository.TenantLimitOverrideRepository;
import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.repository.PricingPlanRepository;
import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpaySubscriptionOrder;
import com.desitech.vyaparsathi.subscriptions.razorpay.repository.RazorpaySubscriptionOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformEntitlementService {

    private final PlatformFeatureFlagRepository platformFeatureFlagRepository;
    private final TenantFeatureFlagRepository tenantFeatureFlagRepository;
    private final TenantLimitOverrideRepository tenantLimitOverrideRepository;
    private final RazorpaySubscriptionOrderRepository subscriptionOrderRepository;
    private final PricingPlanRepository pricingPlanRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * Feature Flag Resolution Hierarchy:
     * 1. Tenant Explicit Override
     * 2. Plan Tier Default (active subscription plan configuration)
     * 3. Global Platform Feature Default
     * 4. false if unconfigured
     */
    @Transactional(readOnly = true)
    public boolean isFeatureEnabled(Long shopId, String featureKey) {
        // 1. Tenant Explicit Override
        if (shopId != null) {
            var tenantOverride = tenantFeatureFlagRepository.findByShopIdAndFeatureKey(shopId, featureKey);
            if (tenantOverride.isPresent()) {
                return tenantOverride.get().getEnabled();
            }

            // 2. Active Subscription's Plan Tier Default
            var activeOrderOpt = subscriptionOrderRepository.findTopByShopIdAndStatusOrderByCreatedAtDesc(shopId, "ACTIVE");
            if (activeOrderOpt.isPresent()) {
                Tier tier = Tier.fromString(activeOrderOpt.get().getPlanCode());
                PricingPlanConfig planConfig = pricingPlanRepository.findById(tier).orElse(null);
                if (planConfig != null && planConfig.getFeatures() != null) {
                    boolean planHasFeature = planConfig.getFeatures().stream()
                            .anyMatch(f -> f.equalsIgnoreCase(featureKey) || f.toLowerCase().contains(featureKey.toLowerCase()));
                    if (planHasFeature) {
                        return true;
                    }
                }
            }
        }

        // 3. Global Platform Default
        return platformFeatureFlagRepository.findById(featureKey)
                .map(PlatformFeatureFlag::getDefaultEnabled)
                .orElse(false);
    }

    @Transactional
    public void setTenantFeatureOverride(Long shopId, String featureKey, boolean enabled, Long adminId, String adminUsername) {
        TenantFeatureFlag flag = tenantFeatureFlagRepository.findByShopIdAndFeatureKey(shopId, featureKey)
                .orElseGet(() -> {
                    TenantFeatureFlag f = new TenantFeatureFlag();
                    f.setShopId(shopId);
                    f.setFeatureKey(featureKey);
                    return f;
                });

        boolean oldVal = Boolean.TRUE.equals(flag.getEnabled());
        flag.setEnabled(enabled);
        flag.setUpdatedByAdminId(adminId);
        flag.setUpdatedAt(LocalDateTime.now());
        tenantFeatureFlagRepository.save(flag);

        // Audit log
        AuditLog audit = new AuditLog();
        audit.setUsername(adminUsername);
        audit.setAction("FEATURE_FLAG_OVERRIDE");
        audit.setEntity("TenantFeatureFlag");
        audit.setEntityId(featureKey);
        audit.setActorAdminId(adminId);
        audit.setTargetShopId(shopId);
        audit.setPreviousValue(String.valueOf(oldVal));
        audit.setNewValue(String.valueOf(enabled));
        audit.setTimestamp(LocalDateTime.now());
        audit.setDetails("Feature flag " + featureKey + " override set to " + enabled + " for shop " + shopId);
        auditLogRepository.save(audit);
    }

    @Transactional(readOnly = true)
    public Integer getEffectiveLimit(Long shopId, String resourceKey, Integer defaultPlanLimit) {
        List<TenantLimitOverride> activeOverrides = tenantLimitOverrideRepository.findActiveOverrides(shopId, resourceKey, LocalDateTime.now());
        if (!activeOverrides.isEmpty()) {
            return activeOverrides.get(0).getOverrideLimit();
        }
        return defaultPlanLimit;
    }

    @Transactional
    public void addLimitOverride(Long shopId, String resourceKey, int overrideLimit, LocalDateTime endDate, String reason, Long adminId, String adminUsername) {
        TenantLimitOverride override = new TenantLimitOverride();
        override.setShopId(shopId);
        override.setResourceKey(resourceKey);
        override.setOverrideLimit(overrideLimit);
        override.setStartDate(LocalDateTime.now());
        override.setEndDate(endDate);
        override.setIsActive(true);
        override.setReason(reason);
        override.setCreatedByAdminId(adminId);
        override.setCreatedAt(LocalDateTime.now());
        tenantLimitOverrideRepository.save(override);

        AuditLog audit = new AuditLog();
        audit.setUsername(adminUsername);
        audit.setAction("ENTITLEMENT_LIMIT_OVERRIDE");
        audit.setEntity("TenantLimitOverride");
        audit.setEntityId(resourceKey);
        audit.setActorAdminId(adminId);
        audit.setTargetShopId(shopId);
        audit.setNewValue(String.valueOf(overrideLimit));
        audit.setReason(reason);
        audit.setTimestamp(LocalDateTime.now());
        audit.setDetails("Limit override for " + resourceKey + " set to " + overrideLimit + " for shop " + shopId + ". Reason: " + reason);
        auditLogRepository.save(audit);
    }

    @Transactional(readOnly = true)
    public List<PlatformFeatureFlag> getAllPlatformFeatureFlags() {
        return platformFeatureFlagRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<TenantFeatureFlag> getTenantFeatureOverrides(Long shopId) {
        return tenantFeatureFlagRepository.findByShopId(shopId);
    }

    @Transactional(readOnly = true)
    public List<TenantLimitOverride> getTenantLimitOverrides(Long shopId) {
        return tenantLimitOverrideRepository.findByShopIdAndIsActiveTrue(shopId);
    }
}
