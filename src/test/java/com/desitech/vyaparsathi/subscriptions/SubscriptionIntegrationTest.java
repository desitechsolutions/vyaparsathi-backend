package com.desitech.vyaparsathi.subscriptions;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.subscriptions.dto.PaymentRequest;
import com.desitech.vyaparsathi.subscriptions.dto.PendingPaymentDTO;
import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import com.desitech.vyaparsathi.subscriptions.entity.Subscription;
import com.desitech.vyaparsathi.subscriptions.enums.BillingCycle;
import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.repository.PricingPlanRepository;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionPayRepository;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionRepository;
import com.desitech.vyaparsathi.subscriptions.service.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SubscriptionIntegrationTest {

    @Autowired private SubscriptionService subscriptionService;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionPayRepository subscriptionPayRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private PricingPlanRepository pricingPlanRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.desitech.vyaparsathi.common.util.FileStorageService fileStorageService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    private Long savedShopId;

    @BeforeEach
    void initData() {
        // 1. Mock Security Context for ROLE_SUPER_ADMIN (Matches your ShopFilterAspect check)
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin_user", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 2. Clear records
        subscriptionPayRepository.deleteAll();
        subscriptionRepository.deleteAll();
        pricingPlanRepository.deleteAll();

        // 3. Seed Starter Plan Configuration
        PricingPlanConfig starterPlan = new PricingPlanConfig();
        starterPlan.setTier(Tier.STARTER);
        starterPlan.setDisplayName("Starter Plan");
        starterPlan.setMonthlyPrice(499.0);
        starterPlan.setYearlyPrice(4990.0);
        starterPlan.setDiscountPercentage(15);
        starterPlan.setIsActive(true);
        starterPlan.setFeatures(List.of("Billing", "Inventory"));
        starterPlan.setSortOrder(1);
        pricingPlanRepository.save(starterPlan);

        // 4. Create and Save Shop
        Shop shop = new Shop();
        shop.setName("Test Vyapar Shop");
        shop.setCode("VS-TEST-" + System.currentTimeMillis());
        shop.setState("West Bengal");
        shop.setOwnerName("Birendra");
        shop.setCreatedAt(LocalDateTime.now());

        shop = shopRepository.save(shop);
        savedShopId = shop.getId();

        // Initial context set for Phase 1 & 2
        TenantContext.setCurrentShopId(savedShopId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Verify full flow: Trial -> UTR Submission -> Admin Approval")
    void fullSubscriptionLifecycleTest() {
        // --- PHASE 1: TRIAL START ---
        subscriptionService.initiateTrial(savedShopId, Tier.STARTER);
        subscriptionRepository.flush();

        Subscription trialSub = subscriptionRepository.findByShopIdUnfiltered(savedShopId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));
        LocalDateTime trialExpiry = trialSub.getTrialEndDate();

        // --- PHASE 2: UTR SUBMISSION ---
        PaymentRequest request = new PaymentRequest();
        request.setUtrNumber("UTR123456789");
        request.setAmountPaid(499.0);
        request.setPlanTier(Tier.STARTER);
        request.setBillingCycle(BillingCycle.MONTHLY);

        subscriptionService.processUtrSubmission(99L, savedShopId, request);

        // IMPORTANT: Flush Phase 2 changes while TenantContext (savedShopId) is still active
        // This prevents the Listener from failing when Admin triggers an auto-flush later
        subscriptionRepository.flush();

        // --- PHASE 3: ADMIN APPROVAL ---
        // Clear Shop Context to simulate a global Admin who isn't "logged in" to a specific shop
        TenantContext.clear();

        List<PendingPaymentDTO> pending = subscriptionService.getAllPendingVerifications();
        assertFalse(pending.isEmpty(), "Pending verification should exist");

        // This will internally use TenantContext.setCurrentShopId(pv.getShopId())
        subscriptionService.activateSubscription(pending.get(0).getId(), "SuperAdmin_Birendra");

        // --- PHASE 4: VERIFY ---
        // Verify as admin (unfiltered)
        Subscription activeSub = subscriptionRepository.findByShopIdUnfiltered(savedShopId)
                .orElseThrow(() -> new RuntimeException("Active subscription not found"));

        // Expected: Trial Expiry + 30 Days (Monthly)
        LocalDateTime expectedEnd = trialExpiry.plusDays(30).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime actualEnd = activeSub.getEndDate().truncatedTo(ChronoUnit.SECONDS);

        assertEquals(SubscriptionStatus.ACTIVE, activeSub.getStatus());
        assertEquals(expectedEnd, actualEnd, "End date should stack: Trial Expiry + 30 Days");
        assertNull(activeSub.getTrialEndDate(), "Trial end date should be cleared");
        assertEquals("UTR123456789", activeSub.getLastUtr());
    }
}