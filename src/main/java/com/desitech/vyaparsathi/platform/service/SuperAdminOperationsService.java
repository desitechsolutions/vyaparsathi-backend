package com.desitech.vyaparsathi.platform.service;

import com.desitech.vyaparsathi.audit.entity.AuditLog;
import com.desitech.vyaparsathi.audit.repository.AuditLogRepository;
import com.desitech.vyaparsathi.auth.repository.RefreshTokenRepository;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.platform.dto.ExecutiveMetricsDto;
import com.desitech.vyaparsathi.platform.dto.Shop360Dto;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.repository.PricingPlanRepository;
import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpaySubscriptionOrder;
import com.desitech.vyaparsathi.subscriptions.razorpay.repository.RazorpaySubscriptionOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminOperationsService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RazorpaySubscriptionOrderRepository subscriptionOrderRepository;
    private final com.desitech.vyaparsathi.subscriptions.repository.SubscriptionRepository subscriptionRepository;
    private final com.desitech.vyaparsathi.subscriptions.repository.SubscriptionPayRepository subscriptionPayRepository;
    private final PricingPlanRepository pricingPlanRepository;
    private final ShopStatusCacheManager shopStatusCacheManager;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public ExecutiveMetricsDto getExecutiveMetrics() {
        long totalShops = shopRepository.count();
        long activeShops = shopRepository.findAll().stream().filter(s -> Boolean.TRUE.equals(s.getActive())).count();
        long suspendedShops = totalShops - activeShops;

        // Collect active orders from Razorpay AutoPay
        List<RazorpaySubscriptionOrder> activeRazorpayOrders = subscriptionOrderRepository.findAll().stream()
                .filter(o -> "ACTIVE".equalsIgnoreCase(o.getStatus()))
                .toList();

        // Collect active subscriptions from core Subscription entity (manual/UTR/direct)
        List<com.desitech.vyaparsathi.subscriptions.entity.Subscription> activeCoreSubs = subscriptionRepository.findAll().stream()
                .filter(s -> s.getStatus() == com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus.ACTIVE
                        && s.getEndDate() != null && s.getEndDate().isAfter(LocalDateTime.now()))
                .toList();

        java.util.Set<Long> payingShopIds = new java.util.HashSet<>();
        activeRazorpayOrders.forEach(o -> payingShopIds.add(o.getShopId()));
        activeCoreSubs.forEach(s -> {
            if (s.getShop() != null) {
                payingShopIds.add(s.getShop().getId());
            }
        });

        long payingShops = payingShopIds.size();
        long trialShops = Math.max(0, activeShops - payingShops);

        // Compute MRR & ARR dynamically using actual paid amounts or template prices
        BigDecimal mrr = BigDecimal.ZERO;

        // 1. MRR from Razorpay Orders
        for (RazorpaySubscriptionOrder order : activeRazorpayOrders) {
            Tier tier = Tier.fromString(order.getPlanCode());
            PricingPlanConfig planConfig = pricingPlanRepository.findById(tier).orElse(null);

            if (planConfig != null) {
                BigDecimal monthlyRate;
                if ("YEARLY".equalsIgnoreCase(order.getBillingCycle())) {
                    Double yearlyPrice = planConfig.getYearlyPrice() != null ? planConfig.getYearlyPrice() : 0.0;
                    monthlyRate = BigDecimal.valueOf(yearlyPrice).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
                } else {
                    Double monthlyPrice = planConfig.getMonthlyPrice() != null ? planConfig.getMonthlyPrice() : 0.0;
                    monthlyRate = BigDecimal.valueOf(monthlyPrice);
                }
                mrr = mrr.add(monthlyRate);
            }
        }

        // 2. MRR from Core Subscriptions (for shops not already counted via Razorpay)
        java.util.Set<Long> razorpayShopIds = activeRazorpayOrders.stream().map(RazorpaySubscriptionOrder::getShopId).collect(java.util.stream.Collectors.toSet());
        for (com.desitech.vyaparsathi.subscriptions.entity.Subscription sub : activeCoreSubs) {
            if (sub.getShop() != null && !razorpayShopIds.contains(sub.getShop().getId())) {
                Long shopId = sub.getShop().getId();
                com.desitech.vyaparsathi.subscriptions.entity.PaymentVerification approvedPv = subscriptionPayRepository
                        .findFirstByShopIdAndStatusOrderBySubmittedAtDesc(shopId, com.desitech.vyaparsathi.subscriptions.enums.PaymentVerificationStatus.APPROVED)
                        .orElse(null);

                BigDecimal monthlyRate = BigDecimal.ZERO;
                if (approvedPv != null && approvedPv.getAmount() != null && approvedPv.getAmount() > 0) {
                    double paidAmount = approvedPv.getAmount();
                    boolean isYearly = approvedPv.getBillingCycle() == com.desitech.vyaparsathi.subscriptions.enums.BillingCycle.YEARLY
                            || sub.getBillingCycle() == com.desitech.vyaparsathi.subscriptions.enums.BillingCycle.YEARLY;

                    if (isYearly) {
                        monthlyRate = BigDecimal.valueOf(paidAmount).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
                    } else {
                        monthlyRate = BigDecimal.valueOf(paidAmount);
                    }
                } else {
                    Tier tier = sub.getTier();
                    PricingPlanConfig planConfig = tier != null ? pricingPlanRepository.findById(tier).orElse(null) : null;
                    if (planConfig != null) {
                        if (sub.getBillingCycle() == com.desitech.vyaparsathi.subscriptions.enums.BillingCycle.YEARLY) {
                            Double yearlyPrice = planConfig.getYearlyPrice() != null ? planConfig.getYearlyPrice() : 0.0;
                            monthlyRate = BigDecimal.valueOf(yearlyPrice).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
                        } else {
                            Double monthlyPrice = planConfig.getMonthlyPrice() != null ? planConfig.getMonthlyPrice() : 0.0;
                            monthlyRate = BigDecimal.valueOf(monthlyPrice);
                        }
                    }
                }
                mrr = mrr.add(monthlyRate);
            }
        }

        BigDecimal arr = mrr.multiply(new BigDecimal("12"));

        double trialConversionRate = totalShops > 0 ? ((double) payingShops / totalShops) * 100.0 : 0.0;

        ExecutiveMetricsDto metrics = new ExecutiveMetricsDto();
        metrics.setTotalShops(totalShops);
        metrics.setActiveShops(activeShops);
        metrics.setTrialShops(trialShops);
        metrics.setSuspendedShops(suspendedShops);
        metrics.setPayingShops(payingShops);
        metrics.setMrr(mrr);
        metrics.setArr(arr);
        metrics.setTrialConversionRate(Math.round(trialConversionRate * 10.0) / 10.0);
        metrics.setSystemStatus("HEALTHY");
        return metrics;
    }

    @Transactional(readOnly = true)
    public Shop360Dto getShop360(Long shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("Shop not found with ID: " + shopId));

        long userCount = userRepository.findAll().stream().filter(u -> u.getShop() != null && shopId.equals(u.getShop().getId())).count();

        RazorpaySubscriptionOrder activeRazorpay = subscriptionOrderRepository.findTopByShopIdAndStatusOrderByCreatedAtDesc(shopId, "ACTIVE")
                .orElse(null);
        com.desitech.vyaparsathi.subscriptions.entity.Subscription coreSub = subscriptionRepository.findByShopIdUnfiltered(shopId).orElse(null);

        Shop360Dto dto = new Shop360Dto();
        dto.setShopId(shop.getId());
        dto.setShopName(shop.getName());
        dto.setShopCode(shop.getCode());
        dto.setOwnerName(shop.getOwnerName());
        dto.setOwnerEmail(shop.getEmail());
        dto.setOwnerPhone(shop.getPhone());
        dto.setGstin(shop.getGstin());
        dto.setAddress(shop.getAddress());
        dto.setState(shop.getState());
        dto.setActive(Boolean.TRUE.equals(shop.getActive()));
        dto.setCreatedAt(shop.getCreatedAt());
        dto.setUserCount(userCount);

        if (coreSub != null && coreSub.getStatus() == com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus.ACTIVE) {
            dto.setCurrentPlanCode(coreSub.getTier() != null ? coreSub.getTier().name() : "PRO");
            dto.setSubscriptionStatus(coreSub.getStatus().name());
            dto.setBillingCycle(coreSub.getBillingCycle() != null ? coreSub.getBillingCycle().name() : "MONTHLY");
            dto.setCurrentPeriodEnd(coreSub.getEndDate());
        } else if (activeRazorpay != null) {
            dto.setCurrentPlanCode(activeRazorpay.getPlanCode());
            dto.setSubscriptionStatus(activeRazorpay.getStatus());
            dto.setBillingCycle(activeRazorpay.getBillingCycle());
            dto.setRazorpaySubscriptionId(activeRazorpay.getRazorpaySubscriptionId());
            dto.setCurrentPeriodEnd(activeRazorpay.getCurrentEnd());
        } else if (coreSub != null && coreSub.getStatus() == com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus.TRIAL) {
            dto.setCurrentPlanCode(coreSub.getTier() != null ? coreSub.getTier().name() : "FREE");
            dto.setSubscriptionStatus("TRIAL");
            dto.setCurrentPeriodEnd(coreSub.getTrialEndDate());
        } else {
            dto.setCurrentPlanCode("FREE");
            dto.setSubscriptionStatus("TRIAL");
        }

        List<AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(a -> shopId.equals(a.getTargetShopId()) || (a.getShop() != null && shopId.equals(a.getShop().getId())))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(20)
                .toList();

        dto.setLifecycleLogs(logs);
        return dto;
    }

    @Transactional
    public void updateShopLifecycleStatus(Long shopId, boolean active, String reason, Long adminId, String adminUsername) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("Shop not found with ID: " + shopId));

        boolean oldStatus = Boolean.TRUE.equals(shop.getActive());
        shop.setActive(active);
        shopRepository.save(shop);

        // Evict shop active status cache immediately for instant enforcement
        shopStatusCacheManager.evictShop(shopId);

        // Revoke all refresh tokens if suspended
        if (!active) {
            userRepository.findAll().stream()
                    .filter(u -> u.getShop() != null && shopId.equals(u.getShop().getId()))
                    .forEach(u -> refreshTokenRepository.deleteByUsername(u.getUsername()));
        }

        // Write AuditLog
        AuditLog audit = new AuditLog();
        audit.setUsername(adminUsername);
        audit.setAction(active ? "SHOP_ACTIVATED" : "SHOP_SUSPENDED");
        audit.setEntity("Shop");
        audit.setEntityId(String.valueOf(shopId));
        audit.setActorAdminId(adminId);
        audit.setTargetShopId(shopId);
        audit.setReason(reason);
        audit.setPreviousValue(oldStatus ? "ACTIVE" : "SUSPENDED");
        audit.setNewValue(active ? "ACTIVE" : "SUSPENDED");
        audit.setTimestamp(LocalDateTime.now());
        audit.setDetails("Shop lifecycle status changed from " + (oldStatus ? "ACTIVE" : "SUSPENDED") + " to " + (active ? "ACTIVE" : "SUSPENDED") + ". Reason: " + reason);
        auditLogRepository.save(audit);
    }
}
