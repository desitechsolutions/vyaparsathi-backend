package com.desitech.vyaparsathi.shop;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.auth.service.MfaService;
import com.desitech.vyaparsathi.auth.service.RefreshTokenService;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.util.FileStorageService;
import com.desitech.vyaparsathi.inventory.repository.CategoryRepository;
import com.desitech.vyaparsathi.rbac.entity.UserShopMembership;
import com.desitech.vyaparsathi.rbac.repository.RoleRepository;
import com.desitech.vyaparsathi.rbac.repository.UserShopMembershipRepository;
import com.desitech.vyaparsathi.rbac.service.MembershipService;
import com.desitech.vyaparsathi.rbac.service.PermissionResolver;
import com.desitech.vyaparsathi.rbac.service.RoleSeedService;
import com.desitech.vyaparsathi.rbac.service.SystemRoleDefinitions;
import com.desitech.vyaparsathi.shop.dto.ShopDto;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.enums.IndustryType;
import com.desitech.vyaparsathi.shop.mapper.ShopMapper;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.shop.service.ShopService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Shop Onboarding & RBAC Lifecycle Tests")
class ShopOnboardingRbacTest {

    @Mock private ShopRepository shopRepository;
    @Mock private UserRepository userRepository;
    @Mock private ShopMapper shopMapper;
    @Mock private CategoryRepository categoryRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private FileStorageService fileStorageService;
    @Mock private MfaService mfaService;
    @Mock private UserShopMembershipRepository membershipRepository;
    @Mock private RoleSeedService roleSeedService;
    @Mock private MembershipService membershipService;

    @InjectMocks
    private ShopService shopService;

    private User currentUser;
    private ShopDto shopDto;
    private Shop shopEntity;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(100L);
        currentUser.setUsername("owner@test.com");
        currentUser.setRole(Role.PENDING_OWNER);

        shopDto = new ShopDto();
        shopDto.setName("Test Kirana Store");
        shopDto.setCode("test-kirana");
        shopDto.setIndustryType(IndustryType.GENERAL.name());

        shopEntity = new Shop();
        shopEntity.setId(1L);
        shopEntity.setName("Test Kirana Store");
        shopEntity.setCode("test-kirana");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Completing onboarding seeds preset roles and creates active OWNER membership")
    void testCompleteOnboarding_SeedsRolesAndCreatesMembership() {
        when(shopRepository.existsByCode("test-kirana")).thenReturn(false);
        when(shopMapper.toEntity(any(ShopDto.class))).thenReturn(shopEntity);
        when(shopRepository.saveAndFlush(any(Shop.class))).thenReturn(shopEntity);
        when(userRepository.save(any(User.class))).thenReturn(currentUser);
        when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        com.desitech.vyaparsathi.auth.entity.RefreshToken mockRefreshToken = new com.desitech.vyaparsathi.auth.entity.RefreshToken();
        mockRefreshToken.setToken("mock-refresh-token");
        when(refreshTokenService.createRefreshToken(eq(currentUser.getUsername()), any())).thenReturn(mockRefreshToken);
        when(jwtUtil.generateAccessToken(eq(currentUser), eq(1L), anyString())).thenReturn("mock-jwt-token");
        when(shopMapper.toDto(any(Shop.class))).thenReturn(shopDto);

        // Execute onboarding
        ShopDto result = shopService.completeOnboarding(shopDto, currentUser, null);

        assertNotNull(result);

        // Verify 1: Roles were seeded for the new shop
        verify(roleSeedService, times(1)).seedPresetRolesForShop(1L);

        // Verify 2: Owner membership was added/reactivated
        verify(membershipService, times(1)).addOrReactivate(
                eq(currentUser), eq(1L), eq("OWNER"), isNull(), eq(true));

        // Verify 3: User role was set to OWNER
        assertEquals(Role.OWNER, currentUser.getRole());
        assertEquals(shopEntity, currentUser.getShop());
    }

    @Test
    @DisplayName("RoleSeedService creates all preset roles with full permissions for the shop")
    void testRoleSeedService_CreatesAllPresetRoles() {
        RoleRepository mockRoleRepo = mock(RoleRepository.class);
        when(mockRoleRepo.findByShopIdAndName(anyLong(), anyString())).thenReturn(Optional.empty());

        RoleSeedService seeder = new RoleSeedService(mockRoleRepo);
        int touched = seeder.seedPresetRolesForShop(1L);

        assertEquals(SystemRoleDefinitions.PRESETS.size(), touched);

        // Verify each preset was saved to the repository
        ArgumentCaptor<com.desitech.vyaparsathi.rbac.entity.Role> captor =
                ArgumentCaptor.forClass(com.desitech.vyaparsathi.rbac.entity.Role.class);
        verify(mockRoleRepo, times(SystemRoleDefinitions.PRESETS.size())).save(captor.capture());

        List<com.desitech.vyaparsathi.rbac.entity.Role> savedRoles = captor.getAllValues();
        Optional<com.desitech.vyaparsathi.rbac.entity.Role> ownerRole = savedRoles.stream()
                .filter(r -> "OWNER".equals(r.getName()))
                .findFirst();

        assertTrue(ownerRole.isPresent());
        assertTrue(ownerRole.get().isSystem());
        assertEquals(1L, ownerRole.get().getShopId());
        assertTrue(ownerRole.get().getPermissions().contains("CUSTOMER_VIEW"));
        assertTrue(ownerRole.get().getPermissions().contains("SALES_VIEW"));
        assertTrue(ownerRole.get().getPermissions().containsAll(SystemRoleDefinitions.ALL_PERMISSIONS));
    }

    @Test
    @DisplayName("PermissionResolver grants OWNER all permissions even if role is missing in DB (Fail-Safe)")
    void testPermissionResolver_OwnerNeverLacksPermissions() {
        UserRepository mockUserRepo = mock(UserRepository.class);
        UserShopMembershipRepository mockMemRepo = mock(UserShopMembershipRepository.class);
        RoleRepository mockRoleRepo = mock(RoleRepository.class);
        RoleSeedService mockRoleSeed = mock(RoleSeedService.class);

        PermissionResolver resolver = new PermissionResolver(mockUserRepo, mockMemRepo, mockRoleRepo, mockRoleSeed);

        User ownerUser = new User();
        ownerUser.setId(100L);
        ownerUser.setUsername("owner@test.com");
        ownerUser.setRole(Role.OWNER);
        ownerUser.setShop(shopEntity);

        when(mockUserRepo.findByUsername("owner@test.com")).thenReturn(Optional.of(ownerUser));

        // Mock SecurityContext with owner
        CustomUserDetails userDetails = new CustomUserDetails(ownerUser);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContext.setCurrentShopId(1L);

        // Case A: No membership row exists yet, and role repository returns empty (simulating desync/new shop)
        when(mockMemRepo.findByUserIdAndShopId(100L, 1L)).thenReturn(Optional.empty());
        when(mockRoleRepo.findByShopIdAndName(1L, "OWNER")).thenReturn(Optional.empty());

        Set<String> permissions = resolver.currentUserPermissions();

        // Must NOT be empty! Must contain critical permissions
        assertFalse(permissions.isEmpty());
        assertTrue(permissions.contains("CUSTOMER_VIEW"));
        assertTrue(permissions.contains("SALES_VIEW"));
        assertTrue(permissions.contains("DASHBOARD_VIEW"));
        assertTrue(permissions.containsAll(SystemRoleDefinitions.ALL_PERMISSIONS));

        // Verify self-heal trigger was called
        verify(mockRoleSeed, atLeastOnce()).seedPresetRolesForShop(1L);
    }
}
