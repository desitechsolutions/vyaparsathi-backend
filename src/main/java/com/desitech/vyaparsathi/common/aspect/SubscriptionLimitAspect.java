package com.desitech.vyaparsathi.common.aspect;

import com.desitech.vyaparsathi.common.annotations.CheckSubscriptionLimit;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.SubscriptionLimitException;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.subscriptions.entity.Subscription;
import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionRepository;
import com.desitech.vyaparsathi.subscriptions.repository.PricingPlanRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
public class SubscriptionLimitAspect {

    private final SubscriptionRepository subRepo;
    private final PricingPlanRepository planRepo;
    private final SaleRepository saleRepo;

    @Before("@annotation(limitAnnotation)")
    public void validateSubscriptionLimit(CheckSubscriptionLimit limitAnnotation) {
        Subscription subscription = null;
        Long shopId = getCurrentShopId();
        /*Subscription sub = subRepo.findByShopId(shopId)
                .orElseThrow(() -> new SubscriptionLimitException("No active subscription found."));
*/
        Optional<Subscription> sub = subRepo.findByShopId(shopId);
        if(sub.isEmpty()){
            subscription = new Subscription();
            subscription.setTier(Tier.FREE);
        }

        else {
            subscription = sub.get();
        }

        PricingPlanConfig config = planRepo.findById(subscription.getTier())
                .orElseThrow(() -> new RuntimeException("Config missing for tier: "));

        String limitType = limitAnnotation.value();

        if ("SALES".equals(limitType)) {
            LocalDateTime startOfMonth = LocalDateTime.now()
                    .with(TemporalAdjusters.firstDayOfMonth())
                    .withHour(0).withMinute(0).withSecond(0);

            long currentSalesCount = saleRepo.countMonthlySalesByShop(shopId, startOfMonth);

            if (currentSalesCount >= config.getMaxSalesPerMonth()) {
                throw new SubscriptionLimitException(
                        String.format("Monthly limit reached! Your %s plan allows %d sales/month. Please upgrade to continue.",
                                subscription.getTier(), config.getMaxSalesPerMonth())
                );
            }
        }
    }

    private Long getCurrentShopId() {
        return TenantContext.getCurrentShopId();
    }
}