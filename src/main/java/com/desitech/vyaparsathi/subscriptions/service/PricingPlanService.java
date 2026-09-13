package com.desitech.vyaparsathi.subscriptions.service;

import com.desitech.vyaparsathi.audit.entity.AuditLog;
import com.desitech.vyaparsathi.audit.repository.AuditLogRepository;
import com.desitech.vyaparsathi.common.exception.SubscriptionException;
import com.desitech.vyaparsathi.subscriptions.dto.PricingPlanDTO;
import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.mapper.PricingPlanMapper;
import com.desitech.vyaparsathi.subscriptions.repository.PricingPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingPlanService {

    private final PricingPlanRepository repository;
    private final PricingPlanMapper mapper;
    private final AuditLogRepository auditLogRepository;

    /**
     * Fetch all active plans for the public pricing page.
     */
    @Transactional(readOnly = true)
    public List<PricingPlanDTO> getActivePlans() {
        log.info("Fetching all active pricing plans");
        List<PricingPlanConfig> plans = repository.findAllActiveWithFeatures();
        return mapper.toDtoList(plans);
    }

    /**
     * Get a specific plan config (used for internal pro-rata calculations).
     */
    @Transactional(readOnly = true)
    public PricingPlanConfig getPlanConfig(Tier tier) {
        return repository.findById(tier)
                .orElseThrow(() -> new RuntimeException("Pricing tier configuration not found for: " + tier));
    }

    /**
     * Admin functionality: Update or Create a plan configuration.
     * Optimistically locked via {@code PricingPlanConfig.version} so two admins
     * editing the same tier back-to-back get a clear conflict instead of a lost update,
     * and every successful change is recorded to {@link AuditLog}.
     */
    @Transactional
    public PricingPlanDTO saveOrUpdatePlan(PricingPlanDTO dto) {
        log.info("Updating configuration for tier: {}", dto.getTier());
        boolean isNew = !repository.existsById(dto.getTier());
        PricingPlanConfig entity = repository.findById(dto.getTier())
                .orElseGet(() -> {
                    PricingPlanConfig newConfig = new PricingPlanConfig();
                    newConfig.setTier(dto.getTier());
                    newConfig.setSortOrder(99);
                    return newConfig;
                });

        String previousValue = isNew ? null : describe(entity);

        if (entity.getFeatures() != null && dto.getFeatures() != null) {
            entity.getFeatures().clear();
        }
        mapper.updateEntityFromDto(dto, entity);

        PricingPlanConfig saved;
        try {
            saved = repository.saveAndFlush(entity);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new SubscriptionException(
                    "This plan was changed by someone else in the meantime — please reload and retry.");
        }

        String adminUsername = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "unknown";

        AuditLog audit = new AuditLog();
        audit.setUsername(adminUsername);
        audit.setAction(isNew ? "PRICING_PLAN_CREATE" : "PRICING_PLAN_UPDATE");
        audit.setEntity("PricingPlanConfig");
        audit.setEntityId(saved.getTier().name());
        audit.setPreviousValue(previousValue);
        audit.setNewValue(describe(saved));
        audit.setTimestamp(LocalDateTime.now());
        audit.setDetails("Pricing plan " + saved.getTier() + " " + (isNew ? "created" : "updated") + " by " + adminUsername);
        auditLogRepository.save(audit);

        return mapper.toDto(saved);
    }

    private String describe(PricingPlanConfig p) {
        return "monthlyPrice=" + p.getMonthlyPrice()
                + ", yearlyPrice=" + p.getYearlyPrice()
                + ", discountPercentage=" + p.getDiscountPercentage()
                + ", isActive=" + p.getIsActive()
                + ", canProcessSale=" + p.getCanProcessSale()
                + ", maxSalesPerMonth=" + p.getMaxSalesPerMonth()
                + ", maxItems=" + p.getMaxItems()
                + ", maxStaffUsers=" + p.getMaxStaffUsers()
                + ", promoPriceMonthly=" + p.getPromoPriceMonthly()
                + ", promoPriceYearly=" + p.getPromoPriceYearly()
                + ", promoLabel=" + p.getPromoLabel()
                + ", promoStartsAt=" + p.getPromoStartsAt()
                + ", promoEndsAt=" + p.getPromoEndsAt();
    }

    @Transactional(readOnly = true)
    public PricingPlanDTO getPlanByTier(Tier tier) {
        log.info("Fetching details for tier: {}", tier);
        PricingPlanConfig entity = repository.findByTierWithFeatures(tier)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + tier));
        return mapper.toDto(entity);
    }
}