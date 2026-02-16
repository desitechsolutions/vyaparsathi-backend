package com.desitech.vyaparsathi.subscriptions;

import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.subscriptions.dto.PaymentRequest;
import com.desitech.vyaparsathi.subscriptions.entity.PaymentVerification;
import com.desitech.vyaparsathi.subscriptions.entity.Subscription;
import com.desitech.vyaparsathi.subscriptions.enums.PaymentVerificationStatus;
import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionPayRepository;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionRepository;
import com.desitech.vyaparsathi.subscriptions.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPayRepository subscriptionPayRepository;
    @Mock private ShopRepository shopRepository;

    @InjectMocks private SubscriptionService subscriptionService;

    private Shop testShop;
    private final Long shopId = 1L;
    private final Long userId = 100L;

    @BeforeEach
    void setUp() {
        testShop = new Shop();
        testShop.setId(shopId);
    }

    @Test
    @DisplayName("Should initialize a 14-day trial for new subscription")
    void initiateTrial_NewSubscription_Success() {
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(testShop));
        when(subscriptionRepository.findByShopId(shopId)).thenReturn(Optional.empty());

        subscriptionService.initiateTrial(shopId, Tier.PRO);

        verify(subscriptionRepository, times(1)).save(argThat(sub ->
                sub.getStatus() == SubscriptionStatus.TRIAL &&
                        sub.getTier() == Tier.PRO &&
                        sub.getTrialEndDate().isAfter(LocalDateTime.now().plusDays(13))
        ));
    }

    @Test
    @DisplayName("Should prevent duplicate UTR submission with Conflict status")
    void processUtr_DuplicateUtr_ThrowsResponseStatusException() {
        PaymentRequest request = new PaymentRequest();
        request.setUtrNumber("123456789012");

        when(subscriptionPayRepository.findByUtrNumber("123456789012"))
                .thenReturn(Optional.of(new PaymentVerification()));

        // Service now throws ResponseStatusException
        assertThrows(ResponseStatusException.class, () ->
                subscriptionService.processUtrSubmission(userId, shopId, request)
        );
    }

    @Test
    @DisplayName("Should activate subscription and set status to WAITING in verification")
    void activateSubscription_Success() {
        PaymentVerification pv = new PaymentVerification();
        pv.setId(50L);
        pv.setShopId(shopId);
        pv.setPlanRequested(Tier.PRO); // Use BUSINESS to match your Enum
        pv.setStatus(PaymentVerificationStatus.WAITING);

        Subscription sub = new Subscription();
        sub.setShop(testShop);

        when(subscriptionPayRepository.findById(50L)).thenReturn(Optional.of(pv));
        when(subscriptionRepository.findByShopId(shopId)).thenReturn(Optional.of(sub));

        subscriptionService.activateSubscription(50L, "Admin_Rahul");

        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertEquals(Tier.PRO, sub.getTier());
        assertTrue(sub.getEndDate().isAfter(LocalDateTime.now().plusDays(28)));

        // Verify PV status updated to APPROVED
        verify(subscriptionPayRepository).save(argThat(p -> p.getStatus() == PaymentVerificationStatus.APPROVED));
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
    @DisplayName("Reject should set PV to REJECTED and Subscription to EXPIRED")
    void rejectSubscription_SetsExpired() {
        PaymentVerification pv = new PaymentVerification();
        pv.setShopId(shopId);
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.PENDING);

        when(subscriptionPayRepository.findById(1L)).thenReturn(Optional.of(pv));
        when(subscriptionRepository.findByShopId(shopId)).thenReturn(Optional.of(sub));

        subscriptionService.rejectSubscription(1L, "Invalid UTR");

        assertEquals(SubscriptionStatus.EXPIRED, sub.getStatus());
        verify(subscriptionPayRepository).save(argThat(p -> p.getStatus() == PaymentVerificationStatus.REJECTED));
        verify(subscriptionRepository).save(sub);
    }
}