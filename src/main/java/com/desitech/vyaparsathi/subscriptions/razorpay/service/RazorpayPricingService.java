package com.desitech.vyaparsathi.subscriptions.razorpay.service;

import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.repository.PricingPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Bridges VyaparSathi's {@link PricingPlanConfig} (keyed by {@link Tier} enum)
 * to the flat (planCode, billingCycle) → price lookup required by the Razorpay
 * subscription service.
 *
 * <p>VyaparSathi pricing is admin-configurable in the {@code pricing_plan_configs}
 * DB table. All amounts are stored in INR; this service returns a {@link BigDecimal}
 * in INR. The caller is responsible for converting to paise (× 100) before sending
 * to the Razorpay API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayPricingService {

    private final PricingPlanRepository pricingPlanRepository;

    /**
     * Returns the price (INR, full rupees) for the given plan and billing cycle.
     *
     * @param planCode    VyaparSathi {@link Tier} name — STARTER, PRO, or ENTERPRISE
     * @param billingCycle MONTHLY or YEARLY
     * @return price in INR as a {@link BigDecimal}, never {@code null}
     * @throws IllegalArgumentException if the planCode is not a valid {@link Tier} or
     *                                  the plan is not found in the database
     */
    @Transactional(readOnly = true)
    public BigDecimal getPrice(String planCode, String billingCycle) {
        Tier tier;
        try {
            tier = Tier.valueOf(planCode.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid plan code: " + planCode
                    + ". Must be one of: STARTER, PRO, ENTERPRISE");
        }

        PricingPlanConfig config = pricingPlanRepository.findById(tier)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pricing plan configuration not found for tier: " + tier));

        Double price = "YEARLY".equalsIgnoreCase(billingCycle)
                ? config.getYearlyPrice()
                : config.getMonthlyPrice();

        if (price == null || price <= 0) {
            throw new IllegalArgumentException(
                    "Price is not configured for tier=" + tier + ", cycle=" + billingCycle);
        }

        log.debug("[RAZORPAY-PRICING] tier={}, cycle={}, price={}",
                tier, billingCycle, price);
        return BigDecimal.valueOf(price);
    }
}
