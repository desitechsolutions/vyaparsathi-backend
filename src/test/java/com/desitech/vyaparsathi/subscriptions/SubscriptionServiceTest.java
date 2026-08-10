package com.desitech.vyaparsathi.subscriptions;

import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.common.exception.SubscriptionException;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.subscriptions.dto.PaymentRequest;
import com.desitech.vyaparsathi.subscriptions.entity.PaymentVerification;
import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import com.desitech.vyaparsathi.subscriptions.entity.Subscription;
import com.desitech.vyaparsathi.subscriptions.enums.BillingCycle;
import com.desitech.vyaparsathi.subscriptions.enums.PaymentVerificationStatus;
import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionPayRepository;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionRepository;
import com.desitech.vyaparsathi.subscriptions.service.PricingPlanService;
import com.desitech.vyaparsathi.subscriptions.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPayRepository subscriptionPayRepository;
    @Mock private com.desitech.vyaparsathi.subscriptions.razorpay.repository.RazorpaySubscriptionOrderRepository subscriptionOrderRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private UserRepository userRepository;
    @Mock private PricingPlanService pricingPlanService;

    @InjectMocks private SubscriptionService subscriptionService;

    private Shop testShop;
    private PricingPlanConfig starterPlan;
    private PricingPlanConfig proPlan;
    private final Long shopId = 1L;
    private final Long userId = 100L;

    @BeforeEach
    void setUp() {
        testShop = new Shop();
        testShop.setId(shopId);

        starterPlan = new PricingPlanConfig();
        starterPlan.setTier(Tier.STARTER);
        starterPlan.setMonthlyPrice(500.0);
        starterPlan.setYearlyPrice(5000.0);

        proPlan = new PricingPlanConfig();
        proPlan.setTier(Tier.PRO);
        proPlan.setMonthlyPrice(1000.0);
        proPlan.setYearlyPrice(10000.0);
    }

    @Test
    @DisplayName("Should initialize a 14-day trial and set usedTrial to true")
    void initiateTrial_NewSubscription_Success() {
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(testShop));
        when(subscriptionRepository.findByShopId(shopId)).thenReturn(Optional.empty());

        subscriptionService.initiateTrial(shopId, Tier.PRO);

        verify(subscriptionRepository, times(1)).save(argThat(sub ->
                sub.getStatus() == SubscriptionStatus.TRIAL &&
                        sub.getTier() == Tier.PRO &&
                        sub.isUsedTrial() &&
                        sub.getTrialEndDate().isAfter(LocalDateTime.now().plusDays(13))
        ));
    }

    @Test
    @DisplayName("Should activate yearly plan and bypass shop filter for Admin")
    void activateSubscription_Yearly_Success() {
        PaymentVerification pv = new PaymentVerification();
        pv.setId(50L);
        pv.setShopId(shopId);
        pv.setBillingCycle(BillingCycle.YEARLY);
        pv.setPlanRequested(Tier.PRO);
        pv.setStatus(PaymentVerificationStatus.WAITING);

        Subscription sub = new Subscription();
        sub.setTier(Tier.STARTER);
        sub.setStatus(SubscriptionStatus.PENDING);

        when(pricingPlanService.getPlanConfig(Tier.PRO)).thenReturn(proPlan);
        when(subscriptionPayRepository.findById(50L)).thenReturn(Optional.of(pv));
        // Use Unfiltered method to match Service logic
        when(subscriptionRepository.findByShopIdUnfiltered(shopId)).thenReturn(Optional.of(sub));

        subscriptionService.activateSubscription(50L, "Admin_Rahul");

        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertEquals(BillingCycle.YEARLY, sub.getBillingCycle());
        assertTrue(sub.getEndDate().isAfter(LocalDateTime.now().plusDays(360)));
    }

    @Test
    @DisplayName("Should handle upgrade logic with pro-rata credit")
    void activateSubscription_Upgrade_CalculatesCredit() {
        PaymentVerification pv = new PaymentVerification();
        pv.setId(60L);
        pv.setShopId(shopId);
        pv.setBillingCycle(BillingCycle.MONTHLY);
        pv.setPlanRequested(Tier.PRO);
        pv.setStatus(PaymentVerificationStatus.WAITING);

        Subscription sub = new Subscription();
        sub.setTier(Tier.STARTER);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        // User has 10 days left of Starter
        sub.setEndDate(LocalDateTime.now().plusDays(10));
        sub.setBillingCycle(BillingCycle.MONTHLY);

        when(pricingPlanService.getPlanConfig(Tier.STARTER)).thenReturn(starterPlan);
        when(pricingPlanService.getPlanConfig(Tier.PRO)).thenReturn(proPlan);
        when(subscriptionPayRepository.findById(60L)).thenReturn(Optional.of(pv));
        when(subscriptionRepository.findByShopIdUnfiltered(shopId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findByShopId(shopId)).thenReturn(Optional.of(sub)); // For isShopPremium check

        subscriptionService.activateSubscription(60L, "Admin_Rahul");

        // Pro Plan is 2x price of Starter. 10 days of Starter ~ 5 days of Pro credit.
        // Paid 30 days + ~5 days credit = ~35 days.
        long totalDays = ChronoUnit.DAYS.between(LocalDateTime.now(), sub.getEndDate());
        assertTrue(totalDays >= 34 && totalDays <= 36);
        assertEquals(Tier.PRO, sub.getTier());
    }

    @Test
    @DisplayName("Should prevent duplicate UTR submission")
    void processUtr_DuplicateUtr_ThrowsException() {
        PaymentRequest request = new PaymentRequest();
        request.setUtrNumber("123456789012");

        when(subscriptionPayRepository.findByUtrNumber("123456789012"))
                .thenReturn(Optional.of(new PaymentVerification()));

        assertThrows(SubscriptionException.class, () ->
                subscriptionService.processUtrSubmission(userId, shopId, request)
        );
    }

    @Test
    @DisplayName("isPremium should return true for valid Trial")
    void isShopPremium_TrialActive_ReturnsTrue() {
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.TRIAL);
        sub.setTrialEndDate(LocalDateTime.now().plusDays(5));

        when(subscriptionRepository.findByShopId(shopId)).thenReturn(Optional.of(sub));

        boolean result = subscriptionService.isShopPremium(shopId);
        assertTrue(result);
    }

    @Test
    @DisplayName("Reject should set PV to REJECTED")
    void rejectSubscription_SetsRejected() {
        PaymentVerification pv = new PaymentVerification();
        pv.setShopId(shopId);
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.PENDING);

        when(subscriptionPayRepository.findById(1L)).thenReturn(Optional.of(pv));
        // Reject logic might check premium status
        when(subscriptionRepository.findByShopId(shopId)).thenReturn(Optional.of(sub));

        subscriptionService.rejectSubscription(1L, "Invalid UTR");

        verify(subscriptionPayRepository).save(argThat(p -> p.getStatus() == PaymentVerificationStatus.REJECTED));
    }
}