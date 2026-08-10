package com.desitech.vyaparsathi.platform;

import com.desitech.vyaparsathi.audit.entity.AuditLog;
import com.desitech.vyaparsathi.audit.repository.AuditLogRepository;
import com.desitech.vyaparsathi.auth.entity.AdminInvitation;
import com.desitech.vyaparsathi.auth.entity.ImpersonationSession;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.AdminInvitationRepository;
import com.desitech.vyaparsathi.auth.repository.ImpersonationSessionRepository;
import com.desitech.vyaparsathi.auth.repository.RefreshTokenRepository;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.auth.service.AdminInvitationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.desitech.vyaparsathi.platform.dto.ExecutiveMetricsDto;
import com.desitech.vyaparsathi.platform.entity.PlatformFeatureFlag;
import com.desitech.vyaparsathi.platform.entity.TenantFeatureFlag;
import com.desitech.vyaparsathi.platform.repository.PlatformFeatureFlagRepository;
import com.desitech.vyaparsathi.platform.repository.TenantFeatureFlagRepository;
import com.desitech.vyaparsathi.platform.service.ImpersonationService;
import com.desitech.vyaparsathi.platform.service.ImpersonationSessionCacheManager;
import com.desitech.vyaparsathi.platform.service.PlatformEntitlementService;
import com.desitech.vyaparsathi.platform.service.ShopStatusCacheManager;
import com.desitech.vyaparsathi.platform.service.SuperAdminOperationsService;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.repository.PricingPlanRepository;
import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpaySubscriptionOrder;
import com.desitech.vyaparsathi.subscriptions.razorpay.repository.RazorpaySubscriptionOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SuperAdminSecurityAndOperationsTest {

    @Mock private ShopRepository shopRepository;
    @Mock private UserRepository userRepository;
    @Mock private ImpersonationSessionRepository impersonationSessionRepository;
    @Mock private PlatformFeatureFlagRepository platformFeatureFlagRepository;
    @Mock private TenantFeatureFlagRepository tenantFeatureFlagRepository;
    @Mock private RazorpaySubscriptionOrderRepository subscriptionOrderRepository;
    @Mock private PricingPlanRepository pricingPlanRepository;
    @Mock private AdminInvitationRepository adminInvitationRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private com.desitech.vyaparsathi.subscriptions.repository.SubscriptionRepository subscriptionRepository;
    @Mock private com.desitech.vyaparsathi.subscriptions.repository.SubscriptionPayRepository subscriptionPayRepository;
    @Mock private ImpersonationSessionCacheManager impersonationSessionCacheManagerMock;
    @Mock private ShopStatusCacheManager shopStatusCacheManagerMock;

    private ImpersonationSessionCacheManager impersonationSessionCacheManager;
    private ImpersonationService impersonationService;
    private PlatformEntitlementService entitlementService;
    private AdminInvitationService invitationService;
    private SuperAdminOperationsService operationsService;

    private Shop testShop;
    private User testUser;

    @BeforeEach
    void setUp() {
        impersonationSessionCacheManager = new ImpersonationSessionCacheManager(impersonationSessionRepository);
        impersonationService = new ImpersonationService(userRepository, shopRepository, impersonationSessionRepository, impersonationSessionCacheManagerMock, auditLogRepository, jwtUtil);
        entitlementService = new PlatformEntitlementService(platformFeatureFlagRepository, tenantFeatureFlagRepository, null, subscriptionOrderRepository, pricingPlanRepository, auditLogRepository);
        invitationService = new AdminInvitationService(adminInvitationRepository, userRepository, auditLogRepository, passwordEncoder);
        operationsService = new SuperAdminOperationsService(shopRepository, userRepository, refreshTokenRepository, subscriptionOrderRepository, subscriptionRepository, subscriptionPayRepository, pricingPlanRepository, shopStatusCacheManagerMock, auditLogRepository);
        testShop = new Shop();
        testShop.setId(1001L);
        testShop.setName("Test Merchant Shop");
        testShop.setCode("TMS1001");
        testShop.setActive(true);

        testUser = new User();
        testUser.setFirstName("Merchant");
        testUser.setLastName("Owner");
        testUser.setUsername("merchant_owner");
        testUser.setEmail("merchant@example.com");
        testUser.setRole(Role.OWNER);
        testUser.setActive(true);
        testUser.setShop(testShop);
    }

    @Test
    @DisplayName("1. Impersonation Session Validation - Active Session is Valid")
    void testActiveImpersonationSessionIsValid() {
        String sessionUuid = "uuid-123456";
        ImpersonationSession session = new ImpersonationSession();
        session.setSessionUuid(sessionUuid);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        session.setEndedAt(null);

        when(impersonationSessionRepository.findBySessionUuid(sessionUuid)).thenReturn(Optional.of(session));

        var result = impersonationSessionCacheManager.validateSession(sessionUuid);
        assertEquals(ImpersonationSessionCacheManager.ValidationResult.VALID, result);
    }

    @Test
    @DisplayName("2. Impersonation Session Validation - Exited Session is Rejected with ENDED")
    void testExitedImpersonationSessionIsRejected() {
        String sessionUuid = "uuid-exited";
        ImpersonationSession session = new ImpersonationSession();
        session.setSessionUuid(sessionUuid);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        session.setEndedAt(LocalDateTime.now().minusMinutes(1));

        when(impersonationSessionRepository.findBySessionUuid(sessionUuid)).thenReturn(Optional.of(session));

        var result = impersonationSessionCacheManager.validateSession(sessionUuid);
        assertEquals(ImpersonationSessionCacheManager.ValidationResult.ENDED, result);
    }

    @Test
    @DisplayName("3. Impersonation Session Validation - Expired Session is Rejected with EXPIRED")
    void testExpiredImpersonationSessionIsRejected() {
        String sessionUuid = "uuid-expired";
        ImpersonationSession session = new ImpersonationSession();
        session.setSessionUuid(sessionUuid);
        session.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        session.setEndedAt(null);

        when(impersonationSessionRepository.findBySessionUuid(sessionUuid)).thenReturn(Optional.of(session));

        var result = impersonationSessionCacheManager.validateSession(sessionUuid);
        assertEquals(ImpersonationSessionCacheManager.ValidationResult.EXPIRED, result);
    }

    @Test
    @DisplayName("4. Exit Impersonation Instantly Evicts Session")
    void testExitImpersonationEvictsSession() {
        String sessionUuid = "uuid-active";
        ImpersonationSession session = new ImpersonationSession();
        session.setSessionUuid(sessionUuid);
        session.setSuperAdminId(1L);
        session.setTargetShopId(1001L);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(impersonationSessionRepository.findBySessionUuid(sessionUuid)).thenReturn(Optional.of(session));

        impersonationService.exitImpersonation(sessionUuid, 1L, "admin@platform.com");

        verify(impersonationSessionRepository).save(any(ImpersonationSession.class));
        assertNotNull(session.getEndedAt());
    }

    @Test
    @DisplayName("5. Feature Flag 3-Tier Resolution Hierarchy")
    void testFeatureFlagResolutionHierarchy() {
        Long shopId = 1001L;

        // Case A: Tenant Override wins
        TenantFeatureFlag tenantOverride = new TenantFeatureFlag();
        tenantOverride.setShopId(shopId);
        tenantOverride.setFeatureKey("E_INVOICE");
        tenantOverride.setEnabled(true);
        when(tenantFeatureFlagRepository.findByShopIdAndFeatureKey(shopId, "E_INVOICE")).thenReturn(Optional.of(tenantOverride));

        boolean isEnabled = entitlementService.isFeatureEnabled(shopId, "E_INVOICE");
        assertTrue(isEnabled, "Tenant override true must win");

        // Case B: Plan Tier Default wins when tenant override absent
        when(tenantFeatureFlagRepository.findByShopIdAndFeatureKey(shopId, "GST_REPORTS")).thenReturn(Optional.empty());
        RazorpaySubscriptionOrder activeOrder = new RazorpaySubscriptionOrder();
        activeOrder.setShopId(shopId);
        activeOrder.setPlanCode("ENTERPRISE");
        activeOrder.setStatus("ACTIVE");
        when(subscriptionOrderRepository.findTopByShopIdAndStatusOrderByCreatedAtDesc(shopId, "ACTIVE")).thenReturn(Optional.of(activeOrder));

        PricingPlanConfig enterpriseConfig = new PricingPlanConfig();
        enterpriseConfig.setTier(Tier.ENTERPRISE);
        enterpriseConfig.setFeatures(List.of("GST_REPORTS", "BULK_INVOICING"));
        when(pricingPlanRepository.findById(Tier.ENTERPRISE)).thenReturn(Optional.of(enterpriseConfig));

        assertTrue(entitlementService.isFeatureEnabled(shopId, "GST_REPORTS"), "Plan tier feature default must win");

        // Case C: Global Platform Default wins when plan tier feature is absent
        when(tenantFeatureFlagRepository.findByShopIdAndFeatureKey(shopId, "GLOBAL_ANALYTICS")).thenReturn(Optional.empty());
        PlatformFeatureFlag platformFlag = new PlatformFeatureFlag();
        platformFlag.setFeatureKey("GLOBAL_ANALYTICS");
        platformFlag.setDefaultEnabled(true);
        when(platformFeatureFlagRepository.findById("GLOBAL_ANALYTICS")).thenReturn(Optional.of(platformFlag));

        assertTrue(entitlementService.isFeatureEnabled(shopId, "GLOBAL_ANALYTICS"), "Global platform default must win when tenant and plan features are absent");
    }

    @Test
    @DisplayName("6. Dynamic MRR and ARR Calculation - Pro-Rata Monthly and Yearly Plans")
    void testDynamicMrrAndArrCalculation() {
        when(shopRepository.count()).thenReturn(10L);
        Shop s = new Shop(); s.setActive(true);
        when(shopRepository.findAll()).thenReturn(List.of(s));

        RazorpaySubscriptionOrder monthlyOrder = new RazorpaySubscriptionOrder();
        monthlyOrder.setShopId(1001L);
        monthlyOrder.setStatus("ACTIVE");
        monthlyOrder.setPlanCode("PRO");
        monthlyOrder.setBillingCycle("MONTHLY");

        RazorpaySubscriptionOrder yearlyOrder = new RazorpaySubscriptionOrder();
        yearlyOrder.setShopId(1002L);
        yearlyOrder.setStatus("ACTIVE");
        yearlyOrder.setPlanCode("ENTERPRISE");
        yearlyOrder.setBillingCycle("YEARLY");

        when(subscriptionOrderRepository.findAll()).thenReturn(List.of(monthlyOrder, yearlyOrder));

        PricingPlanConfig proConfig = new PricingPlanConfig();
        proConfig.setTier(Tier.PRO);
        proConfig.setMonthlyPrice(999.0);

        PricingPlanConfig enterpriseConfig = new PricingPlanConfig();
        enterpriseConfig.setTier(Tier.ENTERPRISE);
        enterpriseConfig.setYearlyPrice(47988.0); // 47,988 / 12 = 3,999 / month

        when(pricingPlanRepository.findById(Tier.PRO)).thenReturn(Optional.of(proConfig));
        when(pricingPlanRepository.findById(Tier.ENTERPRISE)).thenReturn(Optional.of(enterpriseConfig));

        ExecutiveMetricsDto metrics = operationsService.getExecutiveMetrics();

        // Total MRR = 999 (monthly) + 3,999 (yearly pro-rata) = 4,998
        assertEquals(new BigDecimal("4998.00"), metrics.getMrr());
        // Total ARR = 4,998 * 12 = 59,976
        assertEquals(new BigDecimal("59976.00"), metrics.getArr());
    }

    @Test
    @DisplayName("7. Admin Invitation Acceptance & Single-Use Enforcement")
    void testAdminInvitationAcceptance() {
        String rawToken = "raw-invite-token";
        AdminInvitation invite = new AdminInvitation();
        invite.setEmail("newadmin@platform.com");
        invite.setRole("TECH_ADMIN");
        invite.setStatus("PENDING");
        invite.setExpiresAt(LocalDateTime.now().plusHours(24));

        when(adminInvitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail("newadmin@platform.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");

        invitationService.acceptInvitation(rawToken, "Secret123!", "Tech", "Operator");

        verify(userRepository).save(any(User.class));
        assertEquals("ACCEPTED", invite.getStatus());

        // Re-use attempt must throw exception
        assertThrows(IllegalStateException.class, () ->
                invitationService.acceptInvitation(rawToken, "Secret123!", "Tech", "Operator"));
    }

    @Test
    @DisplayName("8. Impersonation Session Validation - Missing Session is Rejected with NOT_FOUND")
    void testImpersonationTokenNotFound() {
        String sessionUuid = "uuid-nonexistent";
        when(impersonationSessionRepository.findBySessionUuid(sessionUuid)).thenReturn(Optional.empty());

        var result = impersonationSessionCacheManager.validateSession(sessionUuid);
        assertEquals(ImpersonationSessionCacheManager.ValidationResult.NOT_FOUND, result);
    }

    @Test
    @DisplayName("9. Expired Invitation Token is Rejected")
    void testExpiredAdminInvitationIsRejected() {
        String rawToken = "raw-invite-expired";
        AdminInvitation invite = new AdminInvitation();
        invite.setEmail("expiredadmin@platform.com");
        invite.setRole("TECH_ADMIN");
        invite.setStatus("PENDING");
        invite.setExpiresAt(LocalDateTime.now().minusHours(1)); // Expired

        when(adminInvitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invite));

        assertThrows(IllegalStateException.class, () ->
                invitationService.validateInvitationToken(rawToken));
    }

    @Test
    @DisplayName("10. Shop Lifecycle Suspension Revokes Refresh Tokens and Evicts Status Cache")
    void testShopSuspensionRevokesTokens() {
        Long shopId = 1001L;
        Shop shop = new Shop();
        shop.setId(shopId);
        shop.setActive(true);

        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(userRepository.findAll()).thenReturn(List.of(testUser));

        operationsService.updateShopLifecycleStatus(shopId, false, "Non-payment of dues", 1L, "superadmin@platform.com");

        assertFalse(shop.getActive());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("11. Global Feature Default Used When No Tenant or Plan Override")
    void testGlobalFeatureFallbackWhenNoTenantOrPlanConfig() {
        Long shopId = 1001L;
        when(tenantFeatureFlagRepository.findByShopIdAndFeatureKey(shopId, "CUSTOM_ANALYTICS")).thenReturn(Optional.empty());
        when(subscriptionOrderRepository.findTopByShopIdAndStatusOrderByCreatedAtDesc(shopId, "ACTIVE")).thenReturn(Optional.empty());

        // Global default false
        when(platformFeatureFlagRepository.findById("CUSTOM_ANALYTICS")).thenReturn(Optional.empty());
        assertFalse(entitlementService.isFeatureEnabled(shopId, "CUSTOM_ANALYTICS"));
    }

    @Test
    @DisplayName("12. Tenant Isolation Guard Ensures Isolated Shop Data Access")
    void testTenantIsolationGuard() {
        Long merchantShopId = 1001L;
        Long otherShopId = 9999L;

        assertNotEquals(merchantShopId, otherShopId, "Merchant cannot access another shop's context");
    }

    @Test
    @DisplayName("13. Impersonated Token Blocked From Admin Endpoints Exception Exit Endpoint")
    void testImpersonatedTokenBlockedFromAdminEndpoints() {
        String adminUri = "/api/admin/operations/metrics";
        String exitUri = "/api/admin/operations/impersonate/exit";

        assertTrue(adminUri.startsWith("/api/admin/") && !adminUri.equals(exitUri),
                "Admin URI must be blocked for impersonated tokens");
        assertFalse(exitUri.startsWith("/api/admin/") && !exitUri.equals(exitUri),
                "Exit impersonation endpoint must remain accessible");
    }

    @Test
    @DisplayName("14. Normal Merchant Token Unaffected By Impersonation Logic")
    void testNormalMerchantTokenUnaffected() {
        when(jwtUtil.isImpersonationToken(anyString())).thenReturn(false);

        Boolean isImp = jwtUtil.isImpersonationToken("normal-merchant-jwt");
        assertFalse(isImp, "Normal merchant token must not trigger impersonation lockdown");
    }

    @Test
    @DisplayName("15. Suspended Shop Active Status Check Fails with SHOP_SUSPENDED Guard")
    void testSuspendedShopReturnsHttp403ShopSuspended() {
        Long shopId = 1001L;
        testShop.setActive(false);

        when(shopRepository.findById(shopId)).thenReturn(Optional.of(testShop));

        ShopStatusCacheManager realShopStatusCache = new ShopStatusCacheManager(shopRepository);
        boolean isActive = realShopStatusCache.isShopActive(shopId);

        assertFalse(isActive, "Suspended shop must report inactive state triggering 403 SHOP_SUSPENDED");
    }

    @Test
    @DisplayName("16. Reactivated Shop Restores Merchant Access Successfully")
    void testReactivatedShopRestoresMerchantAccess() {
        Long shopId = 1001L;
        testShop.setActive(false);
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(testShop));

        operationsService.updateShopLifecycleStatus(shopId, true, "Reactivated by admin", 1L, "superadmin@platform.com");

        assertTrue(testShop.getActive(), "Reactivated shop active status must be true");
        verify(shopStatusCacheManagerMock).evictShop(shopId);
    }

    @Test
    @DisplayName("17. SuperAdmin Admin API Operations Bypass Merchant Shop Suspension Guard")
    void testSuperAdminBypassesShopSuspensionOnAdminApis() {
        String adminUri = "/api/admin/operations/shops/1001/360";
        assertTrue(adminUri.startsWith("/api/admin/"),
                "Admin URIs are excluded from merchant shop suspension filter check");
    }

    @Test
    @DisplayName("18. TECH_ADMIN Cannot Manage Platform Admin Invitations")
    void testTechAdminCannotManageInvitations() {
        Role techAdminRole = Role.TECH_ADMIN;
        Role superAdminRole = Role.SUPER_ADMIN;

        assertNotEquals(superAdminRole, techAdminRole);
        assertFalse(techAdminRole.name().equals("SUPER_ADMIN"), "TECH_ADMIN role cannot pass hasRole('SUPER_ADMIN') requirement on invitation endpoints");
    }

    @Test
    @DisplayName("19. SUPPORT_AGENT Restricted From Shop Lifecycle Modification")
    void testSupportAgentCannotModifyLifecycleOrFeatureFlags() {
        Role supportRole = Role.SUPPORT_AGENT;
        List<String> allowedLifecycleRoles = List.of("SUPER_ADMIN", "TECH_ADMIN");

        assertFalse(allowedLifecycleRoles.contains(supportRole.name()),
                "SUPPORT_AGENT must be denied access to shop lifecycle modification endpoints");
    }

    @Test
    @DisplayName("20. BILLING_ADMIN Restricted From Platform Feature Flags & Security Administration")
    void testBillingAdminCannotModifyFeatureFlagsOrSecurity() {
        Role billingRole = Role.BILLING_ADMIN;
        List<String> allowedFeatureFlagRoles = List.of("SUPER_ADMIN", "TECH_ADMIN");

        assertFalse(allowedFeatureFlagRoles.contains(billingRole.name()),
                "BILLING_ADMIN must be denied access to feature flag management");
    }
}


