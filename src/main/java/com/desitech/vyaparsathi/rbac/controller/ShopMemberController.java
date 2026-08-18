package com.desitech.vyaparsathi.rbac.controller;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.service.MfaService;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.rbac.annotation.RequirePermission;
import com.desitech.vyaparsathi.rbac.dto.ShopMemberDto;
import com.desitech.vyaparsathi.rbac.entity.Role;
import com.desitech.vyaparsathi.rbac.entity.UserShopMembership;
import com.desitech.vyaparsathi.rbac.repository.RoleRepository;
import com.desitech.vyaparsathi.rbac.repository.UserShopMembershipRepository;
import com.desitech.vyaparsathi.rbac.service.MembershipService;
import com.desitech.vyaparsathi.rbac.service.SystemRoleDefinitions;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Team members endpoints — everyone with an active {@link UserShopMembership}
 * for the currently-active shop.
 *
 *   GET    /api/shop/members                   — list everyone in the shop
 *   PATCH  /api/shop/members/{userId}/role     — change their role
 *   PATCH  /api/shop/members/{userId}/status   — activate / deactivate the membership
 *   DELETE /api/shop/members/{userId}          — remove from this shop (soft, keeps user account)
 *
 * OWNER role is protected end-to-end — you cannot demote or remove the
 * shop owner from here (they need to transfer ownership first, which is
 * a separate flow for future phases).
 */
@RestController
@RequestMapping("/api/shop/members")
@RequiredArgsConstructor
public class ShopMemberController {

    private static final Logger log = LoggerFactory.getLogger(ShopMemberController.class);

    private final UserShopMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MembershipService membershipService;
    private final MfaService mfaService;

    @GetMapping
    @RequirePermission("TEAM_VIEW")
    public List<ShopMemberDto> list() {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) return List.of();
        List<UserShopMembership> memberships = membershipRepository.findByShopIdAndActiveTrue(shopId);
        List<ShopMemberDto> out = new ArrayList<>(memberships.size());
        for (UserShopMembership m : memberships) {
            User u = userRepository.findById(m.getUserId()).orElse(null);
            if (u == null) continue;
            out.add(toDto(m, u));
        }
        // OWNER first, ADMIN, MANAGER, … then alphabetical by name.
        out.sort((a, b) -> {
            int oa = SystemRoleDefinitions.orderOf(a.getRole());
            int ob = SystemRoleDefinitions.orderOf(b.getRole());
            if (oa != ob) return Integer.compare(oa, ob);
            String na = (a.getFirstName() != null ? a.getFirstName() : a.getUsername());
            String nb = (b.getFirstName() != null ? b.getFirstName() : b.getUsername());
            return na.compareToIgnoreCase(nb);
        });
        return out;
    }

    @PatchMapping("/{userId}/role")
    @RequirePermission("TEAM_MANAGE")
    public ShopMemberDto changeRole(@PathVariable Long userId,
                                    @RequestBody RoleChangeRequest req,
                                    Authentication auth) {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) throw new IllegalStateException("No active shop context.");
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Team member not found."));
        UserShopMembership mem = membershipRepository.findByUserIdAndShopId(userId, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Team member not found in this shop."));

        guardOwnerActions(target, mem, auth, "role changes");
        if ("OWNER".equalsIgnoreCase(req.getRoleName())) {
            throw new IllegalArgumentException("Ownership can't be granted from here. Use transfer ownership.");
        }
        if (roleRepository.findByShopIdAndName(shopId, req.getRoleName()).isEmpty()) {
            throw new IllegalArgumentException("Role '" + req.getRoleName() + "' does not exist for this shop.");
        }

        membershipService.changeRole(userId, shopId, req.getRoleName());
        log.info("Team-role change: shop={} user={} newRole={}", shopId, userId, req.getRoleName());
        return toDto(membershipRepository.findByUserIdAndShopId(userId, shopId).orElseThrow(), target);
    }

    @PatchMapping("/{userId}/status")
    @RequirePermission("TEAM_MANAGE")
    public ShopMemberDto setStatus(@PathVariable Long userId,
                                   @RequestBody StatusChangeRequest req,
                                   Authentication auth) {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) throw new IllegalStateException("No active shop context.");
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Team member not found."));
        UserShopMembership mem = membershipRepository.findByUserIdAndShopId(userId, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Team member not found in this shop."));

        guardOwnerActions(target, mem, auth, "activation changes");
        if (req.isActive()) {
            membershipService.addOrReactivate(target, shopId, mem.getRole(), mem.getInvitedBy(), false);
        } else {
            membershipService.deactivate(userId, shopId);
        }
        return toDto(membershipRepository.findByUserIdAndShopId(userId, shopId).orElseThrow(), target);
    }

    @DeleteMapping("/{userId}")
    @RequirePermission("TEAM_MANAGE")
    public ApiResponse<Void> remove(@PathVariable Long userId, Authentication auth) {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) throw new IllegalStateException("No active shop context.");
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Team member not found."));
        UserShopMembership mem = membershipRepository.findByUserIdAndShopId(userId, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Team member not found in this shop."));

        guardOwnerActions(target, mem, auth, "removal");
        // Soft-remove: deactivate the membership. The user account itself
        // stays intact — they may still be a member of other shops.
        membershipService.deactivate(userId, shopId);
        log.info("Team member removed: shop={} user={} by actor={}", shopId, userId, auth.getName());
        return new ApiResponse<>("success", "Team member removed from this shop.", null);
    }

    // ─── Internals ───────────────────────────────────────────────────────

    private void guardOwnerActions(User target, UserShopMembership mem, Authentication auth, String actionLabel) {
        if ("OWNER".equalsIgnoreCase(mem.getRole())) {
            throw new IllegalStateException("The shop OWNER is protected from " + actionLabel + ".");
        }
        if (auth != null && auth.getName() != null && auth.getName().equalsIgnoreCase(target.getUsername())) {
            throw new IllegalStateException("You cannot apply " + actionLabel + " to your own account.");
        }
    }

    private ShopMemberDto toDto(UserShopMembership m, User u) {
        String roleDisplay = null;
        if (m.getRoleId() != null) {
            roleDisplay = roleRepository.findById(m.getRoleId()).map(Role::getDisplayName).orElse(null);
        }
        if (roleDisplay == null) {
            SystemRoleDefinitions.RoleSpec spec = SystemRoleDefinitions.PRESETS.get(m.getRole());
            roleDisplay = spec != null ? spec.displayName() : m.getRole();
        }
        return ShopMemberDto.builder()
                .userId(u.getId())
                .membershipId(m.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .phone(u.getPhone())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .userActive(u.isActive())
                .membershipActive(m.isActive())
                .defaultMembership(m.isDefaultMembership())
                .role(m.getRole())
                .roleDisplayName(roleDisplay)
                .mfaEnabled(mfaService.isEnabled(u.getId()))
                .lastLoginAt(u.getLastLoginAt())
                .joinedAt(m.getJoinedAt())
                .invitedBy(m.getInvitedBy())
                .build();
    }

    @Data public static class RoleChangeRequest { private String roleName; }
    @Data public static class StatusChangeRequest { private boolean active; }
}
