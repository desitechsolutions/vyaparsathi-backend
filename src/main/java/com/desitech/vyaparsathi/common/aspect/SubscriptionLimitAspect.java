package com.desitech.vyaparsathi.common.aspect;

import com.desitech.vyaparsathi.common.annotations.CheckSubscriptionLimit;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.FeatureRestrictedException;
import com.desitech.vyaparsathi.inventory.repository.ItemRepository;
import com.desitech.vyaparsathi.rbac.repository.UserShopMembershipRepository;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import com.desitech.vyaparsathi.subscriptions.entity.Subscription;
import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.repository.PricingPlanRepository;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

@Aspect
@Component
@RequiredArgsConstructor
public class SubscriptionLimitAspect {

    private final SubscriptionRepository subRepo;
    private final PricingPlanRepository planRepo;
    private final SaleRepository saleRepo;
    private final ItemRepository itemRepo;
    private final UserShopMembershipRepository membershipRepo;

    @Before("@annotation(limitAnnotation)")
    public void validateSubscriptionLimit(CheckSubscriptionLimit limitAnnotation) {
        Long shopId = getCurrentShopId();
        if (shopId == null) {
            return;
        }

        Subscription sub = subRepo.findByShopId(shopId).orElse(null);
        LocalDateTime now = LocalDateTime.now();

        Tier effectiveTier = Tier.FREE;
        boolean canStartTrial = true;

        if (sub != null) {
            boolean isTrial = sub.getStatus() == SubscriptionStatus.TRIAL;
            boolean isActive = sub.getStatus() == SubscriptionStatus.ACTIVE;
            LocalDateTime targetDate = isTrial ? sub.getTrialEndDate() : sub.getEndDate();

            if ((isTrial || isActive) && targetDate != null && targetDate.isAfter(now)) {
                effectiveTier = sub.getTier() != null ? sub.getTier() : Tier.FREE;
            }

            if (sub.isUsedTrial() || sub.getEndDate() != null) {
                canStartTrial = false;
            }
        }

        PricingPlanConfig config = planRepo.findById(effectiveTier).orElse(null);
        if (config == null) {
            return;
        }

        String limitType = limitAnnotation.value();

        if ("SALES".equals(limitType)) {
            // 1. Check feature toggle restriction
            if (config.getCanProcessSale() != null && !config.getCanProcessSale()) {
                throw new FeatureRestrictedException(
                        "CAN_PROCESS_SALE",
                        "Completing sales is restricted under your current plan configuration.",
                        canStartTrial,
                        14
                );
            }

            // 2. Check quota limit
            if (config.getMaxSalesPerMonth() != null && config.getMaxSalesPerMonth() > 0) {
                LocalDateTime startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth())
                        .withHour(0).withMinute(0).withSecond(0);

                long currentSalesCount = saleRepo.countMonthlySalesByShop(shopId, startOfMonth);

                if (currentSalesCount >= config.getMaxSalesPerMonth()) {
                    throw new FeatureRestrictedException(
                            "CAN_PROCESS_SALE",
                            String.format("Monthly limit reached! Your %s plan allows %d sales/month. Please upgrade to continue.",
                                    effectiveTier, config.getMaxSalesPerMonth()),
                            canStartTrial,
                            14
                    );
                }
            }
        }

        if ("ITEMS".equals(limitType)) {
            if (config.getMaxItems() != null && config.getMaxItems() > 0) {
                long currentItemCount = itemRepo.countByShopId(shopId);
                if (currentItemCount >= config.getMaxItems()) {
                    throw new FeatureRestrictedException(
                            "MAX_ITEMS",
                            String.format("Item limit reached! Your %s plan allows %d items. Please upgrade to add more.",
                                    effectiveTier, config.getMaxItems()),
                            canStartTrial,
                            14
                    );
                }
            }
        }

        if ("STAFF".equals(limitType)) {
            if (config.getMaxStaffUsers() != null && config.getMaxStaffUsers() > 0) {
                long currentStaffCount = membershipRepo.countByShopIdAndActiveTrue(shopId);
                if (currentStaffCount >= config.getMaxStaffUsers()) {
                    throw new FeatureRestrictedException(
                            "MAX_STAFF_USERS",
                            String.format("Staff limit reached! Your %s plan allows %d staff members. Please upgrade to add more.",
                                    effectiveTier, config.getMaxStaffUsers()),
                            canStartTrial,
                            14
                    );
                }
            }
        }
    }

    private Long getCurrentShopId() {
        return TenantContext.getCurrentShopId();
    }
}