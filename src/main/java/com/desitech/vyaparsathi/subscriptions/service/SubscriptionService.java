package com.desitech.vyaparsathi.subscriptions.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.SubscriptionException;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.subscriptions.dto.*;
import com.desitech.vyaparsathi.subscriptions.entity.PaymentVerification;
import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import com.desitech.vyaparsathi.subscriptions.entity.Subscription;
import com.desitech.vyaparsathi.subscriptions.enums.BillingCycle;
import com.desitech.vyaparsathi.subscriptions.enums.PaymentVerificationStatus;
import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.razorpay.repository.RazorpaySubscriptionOrderRepository;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionPayRepository;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPayRepository subscriptionPayRepository;
    private final RazorpaySubscriptionOrderRepository subscriptionOrderRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final PricingPlanService pricingPlanService;

    private static final int TRIAL_DAYS = 14;

    /**
     * Initiate a 14-day trial.
     * Blocked if the user has ALREADY used a trial or has ALREADY been a paid customer.
     */
    @Transactional
    public Subscription initiateTrial(Long shopId, Tier targetTier) {
        if (subscriptionOrderRepository.hasActiveSubscriptionForShop(shopId)) {
            throw new SubscriptionException("Active Razorpay AutoPay subscription exists. Cancel AutoPay mandate before starting a trial.");
        }

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop not found: " + shopId));

        Subscription subscription = subscriptionRepository.findByShopId(shopId).orElse(null);
        LocalDateTime now = LocalDateTime.now();

        if (subscription != null) {
            if (subscription.isUsedTrial() || subscription.getEndDate() != null) {
                throw new SubscriptionException("Trial is only available for new accounts. Please subscribe to a plan.");
            }

            if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                throw new SubscriptionException("Shop already has an active subscription");
            }

            subscription.setTier(targetTier != null ? targetTier : Tier.STARTER);
            subscription.setStatus(SubscriptionStatus.TRIAL);
            subscription.setStartDate(now);
            subscription.setTrialEndDate(now.plusDays(TRIAL_DAYS));
            subscription.setUsedTrial(true);
            return subscriptionRepository.save(subscription);
        } else {
            Subscription s = new Subscription();
            s.setShop(shop);
            s.setTier(targetTier != null ? targetTier : Tier.STARTER);
            s.setStatus(SubscriptionStatus.TRIAL);
            s.setStartDate(now);
            s.setTrialEndDate(now.plusDays(TRIAL_DAYS));
            s.setUsedTrial(true);
            return subscriptionRepository.save(s);
        }
    }

    /**
     * Process UTR submission.
     */
    @Transactional
    public PaymentVerification processUtrSubmission(Long userId, Long shopId, PaymentRequest request) {
        if (subscriptionOrderRepository.hasActiveSubscriptionForShop(shopId)) {
            throw new SubscriptionException("Active Razorpay AutoPay subscription exists. Cancel AutoPay mandate before submitting manual UTR payments.");
        }

        if (subscriptionPayRepository.existsByShopIdAndStatus(shopId, PaymentVerificationStatus.WAITING)) {
            throw new SubscriptionException("A manual UTR payment is currently pending admin verification. Cancel the pending UTR request or wait for verification before submitting another UTR.");
        }

        if (subscriptionPayRepository.findByUtrNumber(request.getUtrNumber()).isPresent()) {
            throw new SubscriptionException("This UTR has already been submitted.");
        }

        PaymentVerification pv = new PaymentVerification();
        pv.setUserId(userId);
        pv.setShopId(shopId);
        pv.setUtrNumber(request.getUtrNumber());
        pv.setAmount(request.getAmountPaid());
        pv.setPlanRequested(request.getPlanTier());
        pv.setBillingCycle(request.getBillingCycle());
        pv.setStatus(PaymentVerificationStatus.WAITING);
        pv.setSubmittedAt(LocalDateTime.now());
        pv = subscriptionPayRepository.save(pv);

        Subscription subscription = subscriptionRepository.findByShopId(shopId).orElseGet(() -> {
            Subscription s = new Subscription();
            s.setShop(shopRepository.findById(shopId).orElseThrow(() -> new SubscriptionException("Shop not found")));
            return s;
        });

        subscription.setTier(request.getPlanTier());
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setLastUtr(request.getUtrNumber());
        subscriptionRepository.save(subscription);

        return pv;
    }

    /**
     * Cancels a pending UTR submission for the shop.
     */
    @Transactional
    public void cancelPendingUtrSubmission(Long shopId) {
        PaymentVerification pv = subscriptionPayRepository
                .findFirstByShopIdAndStatusOrderBySubmittedAtDesc(shopId, PaymentVerificationStatus.WAITING)
                .orElseThrow(() -> new SubscriptionException("No pending UTR verification found to cancel."));

        pv.setStatus(PaymentVerificationStatus.REJECTED);
        pv.setVerifiedAt(LocalDateTime.now());
        pv.setVerifiedBy("USER_CANCELLED");
        subscriptionPayRepository.save(pv);

        subscriptionRepository.findByShopId(shopId).ifPresent(sub -> {
            if (sub.getStatus() == SubscriptionStatus.PENDING) {
                sub.setStatus(SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(sub);
            }
        });
    }

    /**
     * Approves verification and calculates expiry based on amount/cycle.
     */
    @Transactional
    public void activateSubscription(Long verificationId, String adminName) {
        // 1. Fetch verification record
        PaymentVerification pv = subscriptionPayRepository.findById(verificationId)
                .orElseThrow(() -> new NoSuchElementException("Verification record not found"));

        if (pv.getStatus() != PaymentVerificationStatus.WAITING) {
            throw new SubscriptionException("Verification is already processed.");
        }
        TenantContext.setCurrentShopId(pv.getShopId());
        try{

            // 2. Fetch subscription record (Unfiltered for Admin access)
            Subscription sub = subscriptionRepository.findByShopIdUnfiltered(pv.getShopId())
                    .orElseThrow(() -> new SubscriptionException("Subscription record missing for shop: " + pv.getShopId()));

            LocalDateTime now = LocalDateTime.now();
            Tier currentTier = sub.getTier();
            Tier requestedTier = pv.getPlanRequested();
            boolean isAlreadyPremium = isShopPremium(pv.getShopId());

            // 3. Fetch Plan Configurations for math logic
            PricingPlanConfig newPlan = pricingPlanService.getPlanConfig(requestedTier);

            // 4. Determine Paid Days based on billing cycle
            int paidDays = (pv.getBillingCycle() == BillingCycle.YEARLY) ? 365 : 30;
            long creditDays = 0;

            // 5. Pro-Rata / Value Conversion Logic
            if (isAlreadyPremium && isUpgrade(currentTier, requestedTier)) {
                // UPGRADE SCENARIO: Convert remaining low-tier value to high-tier days
                PricingPlanConfig currentPlan = pricingPlanService.getPlanConfig(currentTier);

                LocalDateTime currentExpiry = (sub.getStatus() == SubscriptionStatus.TRIAL)
                        ? sub.getTrialEndDate() : sub.getEndDate();

                if (currentExpiry != null && currentExpiry.isAfter(now)) {
                    long remainingDays = ChronoUnit.DAYS.between(now, currentExpiry);

                    // Calculate banked value: days * daily rate of old plan
                    boolean wasYearly = sub.getBillingCycle() == BillingCycle.YEARLY;
                    double remainingValue = remainingDays * currentPlan.getDailyRate(wasYearly);

                    // Convert value to days of the new plan
                    boolean isNewYearly = pv.getBillingCycle() == BillingCycle.YEARLY;
                    creditDays = Math.round(remainingValue / newPlan.getDailyRate(isNewYearly));
                }

                // Upgrade specific updates
                sub.setPreviousTier(currentTier);
                sub.setLastUpgradeBonusDays((int) creditDays);
                sub.setStartDate(now); // New tier usage starts now

            } else if (isAlreadyPremium && currentTier == requestedTier) {
                // RENEWAL SCENARIO: Simple stacking of time
                sub.setLastUpgradeBonusDays(0);
            } else {
                // FRESH START / DOWNGRADE / POST-EXPIRY SCENARIO
                sub.setStartDate(now);
                sub.setLastUpgradeBonusDays(0);
            }

            // 6. Calculate New Expiry Date
            // Logic: Stack if same-tier renewal, otherwise start from Now/TrialEnd
            LocalDateTime baseDate;
            boolean isCurrentlyInTrial = sub.getTrialEndDate() != null && sub.getTrialEndDate().isAfter(now);
            if (sub.getStatus() == SubscriptionStatus.ACTIVE && currentTier == requestedTier && sub.getEndDate() != null) {
                // Existing paid member renewing same tier: stack on end date
                baseDate = sub.getEndDate();
            } else if (isCurrentlyInTrial && currentTier == requestedTier) {
                // Trial member moving to paid on same tier: stack on trial end date
                baseDate = sub.getTrialEndDate();
            } else {
                // Upgrade, Downgrade, or Fresh start: start from now
                baseDate = now;
            }

            sub.setEndDate(baseDate.plusDays(paidDays).plusDays(creditDays));

            // 7. Finalize Subscription State
            sub.setTier(requestedTier);
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setLastUtr(pv.getUtrNumber());
            sub.setBillingCycle(pv.getBillingCycle());
            sub.setTrialEndDate(null); // Clear trial info once they are a paid member

            subscriptionRepository.save(sub);

            // 8. Update Payment Verification status
            pv.setStatus(PaymentVerificationStatus.APPROVED);
            pv.setVerifiedAt(now);
            pv.setVerifiedBy(adminName);
            subscriptionPayRepository.save(pv);
        }
        finally {
            TenantContext.clear();
        }
    }
    /**
     * Helper to determine if the move is an upgrade
     */
    private boolean isUpgrade(Tier current, Tier requested) {
        if (current == null || current == Tier.FREE) return true;
        if (current == Tier.STARTER && (requested == Tier.PRO || requested == Tier.ENTERPRISE)) return true;
        if (current == Tier.PRO && requested == Tier.ENTERPRISE) return true;
        return false;
    }

    @Transactional
    public void rejectSubscription(Long verificationId, String reason) {
        PaymentVerification pv = subscriptionPayRepository.findById(verificationId)
                .orElseThrow(() -> new NoSuchElementException("Verification record not found"));

        pv.setStatus(PaymentVerificationStatus.REJECTED);
        pv.setVerifiedAt(LocalDateTime.now());
        subscriptionPayRepository.save(pv);

        subscriptionRepository.findByShopId(pv.getShopId()).ifPresent(sub -> {
            if (!isShopPremium(pv.getShopId())) {
                sub.setStatus(SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(sub);
            }
        });
    }

    public boolean isShopPremium(Long shopId) {
        return subscriptionRepository.findByShopId(shopId)
                .map(sub -> {
                    LocalDateTime now = LocalDateTime.now();
                    if (sub.getStatus() == SubscriptionStatus.ACTIVE) {
                        return sub.getEndDate() != null && sub.getEndDate().isAfter(now);
                    }
                    if (sub.getStatus() == SubscriptionStatus.TRIAL) {
                        return sub.getTrialEndDate() != null && sub.getTrialEndDate().isAfter(now);
                    }
                    return false;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public void validateSaleProcessingEntitlement(Long shopId) {
        if (shopId == null) {
            return;
        }

        Subscription sub = subscriptionRepository.findByShopId(shopId).orElse(null);
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

        PricingPlanConfig config = null;
        try {
            config = pricingPlanService.getPlanConfig(effectiveTier);
        } catch (Exception ignored) {}

        boolean canProcess = (config == null || config.getCanProcessSale() == null || Boolean.TRUE.equals(config.getCanProcessSale()));

        if (!canProcess) {
            throw new com.desitech.vyaparsathi.common.exception.FeatureRestrictedException(
                    "CAN_PROCESS_SALE",
                    "Completing sales is restricted under your current plan configuration.",
                    canStartTrial,
                    TRIAL_DAYS
            );
        }
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusDTO getSubscriptionStatus(Long shopId) {
        if (shopId == null) {
            return SubscriptionStatusDTO.builder()
                    .tier(Tier.FREE)
                    .status(SubscriptionStatus.ACTIVE)
                    .premium(false)
                    .daysRemaining(0)
                    .canProcessSale(true)
                    .canStartTrial(true)
                    .build();
        }

        return subscriptionRepository.findByShopId(shopId)
                .map(sub -> {
                    LocalDateTime now = LocalDateTime.now();
                    boolean isTrial = sub.getStatus() == SubscriptionStatus.TRIAL;
                    boolean isActive = sub.getStatus() == SubscriptionStatus.ACTIVE;

                    LocalDateTime targetDate = isTrial ? sub.getTrialEndDate() : sub.getEndDate();
                    long daysRemaining = (targetDate != null) ? ChronoUnit.DAYS.between(now, targetDate) : 0;

                    boolean hasAccess = (isTrial || isActive) && targetDate != null && targetDate.isAfter(now);

                    // Derive effective status in real-time so the API is always accurate,
                    // even if the nightly scheduler hasn't run yet.
                    SubscriptionStatus effectiveStatus = sub.getStatus();
                    if ((isTrial || isActive) && targetDate != null && !targetDate.isAfter(now)) {
                        effectiveStatus = SubscriptionStatus.EXPIRED;
                    }

                    Tier effectiveTier = Tier.FREE;
                    if (hasAccess && sub.getTier() != null) {
                        effectiveTier = sub.getTier();
                    }

                    PricingPlanConfig config = null;
                    try {
                        config = pricingPlanService.getPlanConfig(effectiveTier);
                    } catch (Exception ignored) {}

                    boolean canProcess = (config == null || config.getCanProcessSale() == null || Boolean.TRUE.equals(config.getCanProcessSale()));
                    boolean canStartTrial = !sub.isUsedTrial() && sub.getEndDate() == null;

                    SubscriptionStatusDTO dto = new SubscriptionStatusDTO();
                    dto.setTier(sub.getTier());
                    dto.setStatus(effectiveStatus);
                    dto.setPremium(hasAccess);
                    dto.setDaysRemaining(Math.max(0, daysRemaining));
                    dto.setUsedTrial(sub.isUsedTrial());
                    dto.setLastUtr(sub.getLastUtr());
                    dto.setBillingCycle(sub.getBillingCycle());
                    dto.setCanProcessSale(canProcess);
                    dto.setCanStartTrial(canStartTrial);
                    return dto;
                })
                .orElseGet(() -> {
                    PricingPlanConfig config = null;
                    try {
                        config = pricingPlanService.getPlanConfig(Tier.FREE);
                    } catch (Exception ignored) {}

                    boolean canProcess = (config == null || config.getCanProcessSale() == null || Boolean.TRUE.equals(config.getCanProcessSale()));

                    SubscriptionStatusDTO dto = new SubscriptionStatusDTO();
                    dto.setTier(Tier.FREE);
                    dto.setStatus(null);
                    dto.setPremium(false);
                    dto.setUsedTrial(false);
                    dto.setCanProcessSale(canProcess);
                    dto.setCanStartTrial(true);
                    return dto;
                });
    }

    public List<PendingPaymentDTO> getAllPendingVerifications() {
        return subscriptionPayRepository.findByStatusOrderBySubmittedAtDesc(PaymentVerificationStatus.WAITING)
                .stream().map(pv -> {
                    Shop shop = shopRepository.findById(pv.getShopId()).orElse(null);
                    User user = userRepository.findById(pv.getUserId()).orElse(null);
                    return PendingPaymentDTO.builder()
                            .id(pv.getId()).utrNumber(pv.getUtrNumber()).amount(pv.getAmount())
                            .planRequested(pv.getPlanRequested()).submittedAt(pv.getSubmittedAt())
                            .shopName(shop != null ? shop.getName() : "Unknown")
                            .ownerName(user != null ? user.getFirstName() + " " + user.getLastName() : "Unknown")
                            .build();
                }).toList();
    }

    public PlatformStatsDTO getPlatformStats() {
        return new PlatformStatsDTO(
                subscriptionPayRepository.countByStatus(PaymentVerificationStatus.WAITING),
                shopRepository.count(),
                subscriptionPayRepository.sumApprovedPayments() != null ? subscriptionPayRepository.sumApprovedPayments() : 0.0,
                userRepository.count()
        );
    }

    /**
     * USER METHOD: Fetch payment history for the dashboard table.
     * Maps PaymentVerification entities to DTOs for the UI.
     */
    @Transactional(readOnly = true)
    public List<PaymentVerificationDTO> getShopPaymentHistory(Long shopId) {
        // Note: Ensure findByShopIdOrderBySubmittedAtDesc is defined in SubscriptionPayRepository
        return subscriptionPayRepository.findByShopIdOrderBySubmittedAtDesc(shopId)
                .stream()
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * USER METHOD: Handle subscription cancellation.
     * For manual payments, this prevents future renewal prompts or marks
     * the account to revert to FREE after the current expiry date.
     */
    @Transactional
    public void cancelSubscription(Long shopId) {
        Subscription sub = subscriptionRepository.findByShopId(shopId)
                .orElseThrow(() -> new SubscriptionException("No active subscription found to cancel."));

        if (sub.getStatus() == SubscriptionStatus.EXPIRED) {
            throw new SubscriptionException("Subscription is already expired.");
        }
        sub.setStatus(SubscriptionStatus.CANCELLED);

        subscriptionRepository.save(sub);
    }

    /**
     * Private helper to convert Entity to DTO
     */
    private PaymentVerificationDTO convertToDTO(PaymentVerification pv) {
        return PaymentVerificationDTO.builder()
                .id(pv.getId())
                .utrNumber(pv.getUtrNumber())
                .amount(pv.getAmount())
                .planRequested(pv.getPlanRequested())
                .billingCycle(pv.getBillingCycle())
                .status(pv.getStatus())
                .date(pv.getSubmittedAt())
                .build();
    }
}