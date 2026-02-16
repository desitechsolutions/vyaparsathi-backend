/*
package com.desitech.vyaparsathi.subscriptions;

import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.subscriptions.dto.PaymentRequest;
import com.desitech.vyaparsathi.subscriptions.dto.SubscriptionStatusDTO;
import com.desitech.vyaparsathi.subscriptions.entity.PaymentVerification;
import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionPayRepository;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionRepository;
import com.desitech.vyaparsathi.subscriptions.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    private Long savedShopId;

    @BeforeEach
    void initData() {
        // Create a real shop satisfying all @Column(nullable = false) constraints
        Shop shop = new Shop();
        shop.setName("Test Vyapar Shop");
        shop.setCode("VS-TEST-001");
        shop.setState("West Bengal"); // Mandatory field in your entity
        shop.setOwnerName("Birendra");
        shop.setCreatedAt(LocalDateTime.now()); // Satisfies created_at nullable = false

        shop = shopRepository.save(shop);
        savedShopId = shop.getId();
    }

    @Test
    @DisplayName("Verify full flow: Trial -> UTR Submission -> Admin Approval")
    void fullSubscriptionLifecycleTest() {
        // 1. PHASE: TRIAL START
        // Ensures trial is initiated and access is granted immediately
        subscriptionService.initiateTrial(savedShopId, Tier.STARTER);
        SubscriptionStatusDTO status = subscriptionService.getSubscriptionStatus(savedShopId);

        assertTrue(status.isPremium(), "Shop should have access during 14-day trial");
        assertEquals(SubscriptionStatus.TRIAL, status.getStatus());

        // 2. PHASE: UTR SUBMISSION
        // Simulate user paying via UPI and submitting the HDFC UTR
        PaymentRequest request = new PaymentRequest();
        request.setUtrNumber("UTR123456789");
        request.setAmountPaid(499.0);
        request.setPlanTier(Tier.STARTER);

        subscriptionService.processUtrSubmission(99L, savedShopId, request);

        // Verify that the record is now waiting in the Admin queue
        List<PaymentVerification> pending = subscriptionService.getAllPendingVerifications();
        assertFalse(pending.isEmpty(), "Admin queue should show the pending payment");
        assertEquals("UTR123456789", pending.get(0).getUtrNumber());

        // 3. PHASE: ADMIN APPROVAL
        // Super Admin verifies HDFC statement and clicks 'Approve'
        Long vId = pending.get(0).getId();
        subscriptionService.activateSubscription(vId, "SuperAdmin_Birendra");

        // 4. PHASE: VERIFY FINAL ACTIVE STATUS
        // Shop should now be ACTIVE with 30 days remaining
        SubscriptionStatusDTO finalStatus = subscriptionService.getSubscriptionStatus(savedShopId);
        assertEquals(SubscriptionStatus.ACTIVE, finalStatus.getStatus());
        assertEquals(Tier.STARTER, finalStatus.getTier());
        assertTrue(finalStatus.isPremium(), "Shop should have premium access after approval");
        assertTrue(finalStatus.getDaysRemaining() >= 29, "Should have a full monthly cycle");
    }
}*/
