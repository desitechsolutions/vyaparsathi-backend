package com.desitech.vyaparsathi.rbac.controller;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.rbac.dto.ShopMembershipDto;
import com.desitech.vyaparsathi.rbac.entity.Role;
import com.desitech.vyaparsathi.rbac.entity.UserShopMembership;
import com.desitech.vyaparsathi.rbac.repository.RoleRepository;
import com.desitech.vyaparsathi.rbac.service.MembershipService;
import com.desitech.vyaparsathi.rbac.service.PermissionResolver;
import com.desitech.vyaparsathi.rbac.service.SystemRoleDefinitions;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoints that live around the "which shop am I in?" context.
 *
 *   GET  /api/auth/me/shops          — list every shop the current user belongs to
 *   POST /api/auth/switch-shop       — validate + re-issue JWT with a different shopId claim
 *   GET  /api/auth/me/permissions    — permission codes granted to the current user in the active shop
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class ShopContextController {

    private static final Logger log = LoggerFactory.getLogger(ShopContextController.class);

    private final MembershipService membershipService;
    private final PermissionResolver permissionResolver;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;

    @GetMapping("/me/shops")
    public List<ShopMembershipDto> myShops(Authentication auth) {
        User user = currentUser(auth);
        return membershipService.listActiveForUser(user.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/me/permissions")
    public Map<String, Object> myPermissions(Authentication auth) {
        // TenantContext is already populated by JwtAuthenticationFilter from
        // the JWT's shopId claim, so the resolver just reads it.
        currentUser(auth); // ensures 401 for anonymous
        Map<String, Object> body = new HashMap<>();
        body.put("permissions", permissionResolver.currentUserPermissions());
        return body;
    }

    /**
     * Switch the acting shop. The FE calls this when the user picks a
     * different shop from the switcher; the response's accessToken
     * replaces the current one.
     */
    @PostMapping("/switch-shop")
    public Map<String, Object> switchShop(@RequestBody Map<String, Long> body,
                                          Authentication auth,
                                          jakarta.servlet.http.HttpServletRequest httpRequest) {
        User user = currentUser(auth);
        Long targetShopId = body != null ? body.get("shopId") : null;
        if (targetShopId == null) throw new IllegalArgumentException("shopId is required.");

        UserShopMembership mem = membershipService.find(user.getId(), targetShopId)
                .orElseThrow(() -> new IllegalArgumentException("You are not a member of that shop."));
        if (!mem.isActive()) throw new IllegalStateException("Your access to that shop has been revoked.");

        // Preserve the current session_id across a shop switch — it is
        // still the same user in the same browser, only the tenant
        // context changed. Pull it from the incoming access token via
        // the Authorization header (the filter already validated it).
        String existingSessionId = null;
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            existingSessionId = jwtUtil.extractSessionId(authHeader.substring(7));
        }

        // Re-issue a JWT with the new shopId claim. Everything else (role,
        // subject, first/last name, session_id) is copied straight from
        // the user's active session.
        String newAccessToken = jwtUtil.generateAccessToken(user, targetShopId, existingSessionId);
        log.info("User {} switched active shop to {} (session={})",
                user.getUsername(), targetShopId, existingSessionId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("accessToken", newAccessToken);
        resp.put("shopId", targetShopId);
        resp.put("role", mem.getRole());
        return resp;
    }

    // ─── Utilities ───────────────────────────────────────────────────────

    private User currentUser(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("Not authenticated.");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("User not found."));
    }

    private ShopMembershipDto toDto(UserShopMembership m) {
        Shop shop = shopRepository.findById(m.getShopId()).orElse(null);
        String displayName = null;
        if (m.getRoleId() != null) {
            displayName = roleRepository.findById(m.getRoleId())
                    .map(Role::getDisplayName)
                    .orElse(null);
        }
        if (displayName == null) {
            // Fallback to preset displayName from code
            SystemRoleDefinitions.RoleSpec spec = SystemRoleDefinitions.PRESETS.get(m.getRole());
            displayName = spec != null ? spec.displayName() : m.getRole();
        }
        return ShopMembershipDto.builder()
                .shopId(m.getShopId())
                .shopName(shop != null ? shop.getName() : null)
                .shopCode(shop != null ? shop.getCode() : null)
                .industryType(shop != null && shop.getIndustryType() != null ? shop.getIndustryType().name() : null)
                .logoPath(shop != null ? shop.getLogoPath() : null)
                .role(m.getRole())
                .roleDisplayName(displayName)
                .active(m.isActive())
                .isDefault(m.isDefaultMembership())
                .build();
    }
}
