package com.desitech.vyaparsathi.subscriptions.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.subscriptions.dto.PaymentRequest;
import com.desitech.vyaparsathi.subscriptions.dto.PendingPaymentDTO;
import com.desitech.vyaparsathi.subscriptions.dto.PlatformStatsDTO;
import com.desitech.vyaparsathi.subscriptions.dto.SubscriptionStatusDTO;
import com.desitech.vyaparsathi.subscriptions.entity.PaymentVerification;
import com.desitech.vyaparsathi.subscriptions.entity.Subscription;
import com.desitech.vyaparsathi.subscriptions.enums.PaymentVerificationStatus;
import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
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
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    private static final int TRIAL_DAYS = 14;

    /**
     * Initiate or refresh a 14-day trial for the given shop.
     * Safely updates existing subscription record (no duplicate insert).
     */
    @Transactional
    public Subscription initiateTrial(Long shopId, Tier targetTier) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop not found: " + shopId));

        // Try fetch existing subscription
        Subscription subscription = subscriptionRepository.findByShopId(shopId).orElse(null);

        LocalDateTime now = LocalDateTime.now();

        if (subscription != null) {
            // If already active, disallow starting a trial
            if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop already has an active subscription");
            }

            // If already on trial and not expired, return existing
            if (subscription.getStatus() == SubscriptionStatus.TRIAL && subscription.getTrialEndDate() != null) {
                if (subscription.getTrialEndDate().isAfter(now)) {
                    return subscription;
                }
            }

            // Otherwise update the existing subscription record to set trial fields
            subscription.setTier(targetTier != null ? targetTier : Tier.STARTER);
            subscription.setStatus(SubscriptionStatus.TRIAL);
            subscription.setStartDate(now);
            subscription.setTrialEndDate(now.plusDays(TRIAL_DAYS));
            subscription.setLastUpdatedByUserId(null); // starter action
            return subscriptionRepository.save(subscription);
        } else {
            // No subscription exists — create one
            Subscription s = new Subscription();
            s.setShop(shop);
            s.setTier(targetTier != null ? targetTier : Tier.STARTER);
            s.setStatus(SubscriptionStatus.TRIAL);
            s.setStartDate(now);
            s.setTrialEndDate(now.plusDays(TRIAL_DAYS));
            return subscriptionRepository.save(s);
        }
    }

    /**
     * Process a UTR submission from a shop user. Creates a PaymentVerification
     * entry and marks the shop subscription as PENDING.
     */
    @Transactional
    public PaymentVerification processUtrSubmission(Long userId, Long shopId, PaymentRequest request) {
        // Defensive: check if UTR already exists (global uniqueness)
        if (subscriptionPayRepository.findByUtrNumber(request.getUtrNumber()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This UTR has already been submitted.");
        }

        PaymentVerification pv = new PaymentVerification();
        pv.setUserId(userId);
        pv.setShopId(shopId);
        pv.setUtrNumber(request.getUtrNumber());
        pv.setAmount(request.getAmountPaid());
        pv.setPlanRequested(request.getPlanTier());
        // Keep WAITING to match existing admin pending queries in your codebase
        pv.setStatus(PaymentVerificationStatus.WAITING);
        pv.setSubmittedAt(LocalDateTime.now());
        pv = subscriptionPayRepository.save(pv);

        // Ensure subscription exists and set to PENDING
        Subscription subscription = subscriptionRepository.findByShopId(shopId).orElse(null);
        if (subscription == null) {
            // create a new subscription row (starter) so state is persisted
            subscription = new Subscription();
            subscription.setShop(shopRepository.findById(shopId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop not found")));
            subscription.setTier(request.getPlanTier() != null ? request.getPlanTier() : Tier.STARTER);
        }

        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setLastUtr(request.getUtrNumber());
        subscription.setLastUpdatedByUserId(userId);
        subscriptionRepository.save(subscription);

        return pv;
    }

    /**
     * Admin approves a pending verification. Activation will create or update the subscription
     * for the referenced shop and set the appropriate dates.
     */
    @Transactional
    public void activateSubscription(Long verificationId, String adminName) {
        PaymentVerification pv = subscriptionPayRepository.findById(verificationId)
                .orElseThrow(() -> new NoSuchElementException("Verification record not found"));

        if (pv.getStatus() != PaymentVerificationStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification is not in a waiting state");
        }

        pv.setStatus(PaymentVerificationStatus.APPROVED);
        pv.setVerifiedAt(LocalDateTime.now());
        pv.setVerifiedBy(adminName);
        subscriptionPayRepository.save(pv);

        Long shopId = pv.getShopId();

        Subscription subscription = subscriptionRepository.findByShopId(shopId).orElse(null);
        if (subscription == null) {
            // Create if missing (safer than failing)
            Shop shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop not found: " + shopId));
            subscription = new Subscription();
            subscription.setShop(shop);
        }

        subscription.setTier(pv.getPlanRequested());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(LocalDateTime.now());
        // set a subscription duration (example monthly) — adjust to your pricing rules
        subscription.setEndDate(LocalDateTime.now().plusDays(30));
        subscription.setLastUtr(pv.getUtrNumber());
        subscriptionRepository.save(subscription);
    }

    /**
     * Admin rejects a verification.
     */
    @Transactional
    public void rejectSubscription(Long verificationId, String reason) {
        PaymentVerification pv = subscriptionPayRepository.findById(verificationId)
                .orElseThrow(() -> new NoSuchElementException("Verification record not found"));

        pv.setStatus(PaymentVerificationStatus.REJECTED);
        pv.setVerifiedAt(LocalDateTime.now());
        // Optionally store the reason in a dedicated column or audit table
        subscriptionPayRepository.save(pv);

        // If a subscription exists for this shop, move it to EXPIRED
        subscriptionRepository.findByShopId(pv.getShopId()).ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);
        });
    }

    /**
     * Checks whether the shop currently has premium access (trial or active paid).
     */
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

    /**
     * Build the SubscriptionStatusDTO for the given shop.
     * Returns a DTO with null tier/status when no subscription exists (frontend treats that as FREE).
     */
    @Transactional(readOnly = true)
    public SubscriptionStatusDTO getSubscriptionStatus(Long shopId) {
        return subscriptionRepository.findByShopId(shopId)
                .map(sub -> {
                    LocalDateTime now = LocalDateTime.now();
                    boolean isTrial = sub.getStatus() == SubscriptionStatus.TRIAL;
                    boolean isActive = sub.getStatus() == SubscriptionStatus.ACTIVE;

                    LocalDateTime targetDate = isTrial ? sub.getTrialEndDate() : sub.getEndDate();
                    long daysRemaining = 0;
                    if (targetDate != null) {
                        daysRemaining = ChronoUnit.DAYS.between(now, targetDate);
                    }

                    boolean hasAccess = (isTrial && targetDate != null && targetDate.isAfter(now)) ||
                            (isActive && targetDate != null && targetDate.isAfter(now));

                    SubscriptionStatusDTO dto = new SubscriptionStatusDTO();
                    dto.setTier(sub.getTier());
                    dto.setStatus(sub.getStatus());
                    dto.setPremium(hasAccess);
                    dto.setDaysRemaining(Math.max(0, daysRemaining));
                    dto.setLastUtr(sub.getLastUtr());
                    return dto;
                })
                .orElseGet(() -> {
                    // No subscription -> return DTO with nulls so frontend defaults to 'FREE'
                    SubscriptionStatusDTO dto = new SubscriptionStatusDTO();
                    dto.setTier(null);
                    dto.setStatus(null);
                    dto.setPremium(false);
                    dto.setDaysRemaining(0);
                    dto.setLastUtr(null);
                    return dto;
                });
    }

    /**
     * Admin helper: list pending verifications (waiting)
     */
// Inside SubscriptionService.java

    public List<PendingPaymentDTO> getAllPendingVerifications() {
        List<PaymentVerification> rawList = subscriptionPayRepository.findByStatusOrderBySubmittedAtDesc(PaymentVerificationStatus.WAITING);

        return rawList.stream().map(pv -> {
            // Fetch Shop info
            Shop shop = shopRepository.findById(pv.getShopId()).orElse(null);
            // Fetch User info
            User user = userRepository.findById(pv.getUserId()).orElse(null);

            return PendingPaymentDTO.builder()
                    .id(pv.getId())
                    .utrNumber(pv.getUtrNumber())
                    .amount(pv.getAmount())
                    .planRequested(pv.getPlanRequested())
                    .submittedAt(pv.getSubmittedAt())
                    .shopId(pv.getShopId())
                    .shopName(shop != null ? shop.getName() : "Unknown Shop")
                    .userId(pv.getUserId())
                    .ownerName(user != null ? user.getFirstName() + " " + user.getLastName() : "Unknown User")
                    .ownerEmail(user != null ? user.getEmail() : "N/A")
                    .ownerPhone(user != null ? user.getPhone() : "N/A")
                    .build();
        }).toList();
    }

    public PlatformStatsDTO getPlatformStats() {
        long pending = subscriptionPayRepository.countByStatus(PaymentVerificationStatus.WAITING);
        long shops = shopRepository.count();
        Double revenue = subscriptionPayRepository.sumApprovedPayments();
        long users = userRepository.count();

        return new PlatformStatsDTO(
                pending,
                shops,
                revenue != null ? revenue : 0.0,
                users
        );
    }
}