package com.desitech.vyaparsathi.rbac.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.rbac.entity.UserShopMembership;
import com.desitech.vyaparsathi.rbac.repository.RoleRepository;
import com.desitech.vyaparsathi.rbac.repository.UserShopMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the effective permission set for the acting user in the
 * active shop context. Two resolution paths, tried in order:
 *
 * <ol>
 *   <li><b>Membership + role_id</b> — the user has a {@link UserShopMembership}
 *       for this shop with a {@code role_id} link. We load the {@link
 *       com.desitech.vyaparsathi.rbac.entity.Role} and use its permissions.</li>
 *   <li><b>Legacy bridge</b> — no membership yet, or membership has no
 *       {@code role_id}. Fall back to the user's legacy {@code role}
 *       enum value → look up the same-named system-preset role in this
 *       shop → use its permissions.</li>
 * </ol>
 *
 * <p>Platform-admin roles (SUPER_ADMIN / TECH_ADMIN / BILLING_ADMIN /
 * SUPPORT_AGENT) short-circuit to "all permissions" — they operate
 * across the platform, not within a single tenant's role graph.
 * PENDING_OWNER has no permissions except the ones needed to complete
 * onboarding, which are checked separately at the controller level.
 */
@Service
@RequiredArgsConstructor
public class PermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(PermissionResolver.class);

    private final UserRepository userRepository;
    private final UserShopMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;

    /**
     * @return permission code set for the current SecurityContext user
     *         in the current TenantContext shop. Empty set if there's no
     *         authenticated user or the user has no access to this shop.
     */
    public Set<String> currentUserPermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Collections.emptySet();
        String username = auth.getName();
        if (username == null || "anonymousUser".equalsIgnoreCase(username)) return Collections.emptySet();

        Optional<User> maybeUser = userRepository.findByUsername(username);
        if (maybeUser.isEmpty()) return Collections.emptySet();
        User user = maybeUser.get();

        // Platform admins get the full catalogue — they're not tenant-bound.
        if (isPlatformAdmin(user.getRole())) {
            return SystemRoleDefinitions.ALL_PERMISSIONS;
        }

        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) {
            // No tenant context yet — e.g. PENDING_OWNER routes. Empty set;
            // the specific endpoint's @PreAuthorize takes it from here.
            return Collections.emptySet();
        }

        return permissionsFor(user, shopId);
    }

    /**
     * Same as {@link #currentUserPermissions()} but for a specific
     * (user, shop) pair. Used by admin operations that inspect other
     * users' access.
     */
    public Set<String> permissionsFor(User user, Long shopId) {
        if (user == null || shopId == null) return Collections.emptySet();
        if (isPlatformAdmin(user.getRole())) return SystemRoleDefinitions.ALL_PERMISSIONS;

        // 1. Membership + explicit role_id.
        Optional<UserShopMembership> maybeMem = membershipRepository.findByUserIdAndShopId(user.getId(), shopId);
        if (maybeMem.isPresent()) {
            UserShopMembership mem = maybeMem.get();
            if (!mem.isActive()) return Collections.emptySet();
            if (mem.getRoleId() != null) {
                return roleRepository.findById(mem.getRoleId())
                        .map(r -> r.getPermissions())
                        .orElseGet(() -> permissionsFromLegacyRoleName(shopId, mem.getRole()));
            }
            // Membership exists but no role_id yet — bridge via membership.role name.
            return permissionsFromLegacyRoleName(shopId, mem.getRole());
        }

        // 2. No membership row (very old data) — bridge from users.role.
        return permissionsFromLegacyRoleName(shopId, user.getRole() != null ? user.getRole().name() : null);
    }

    /**
     * True if the user holds a permission in their active shop.
     * Convenience for the {@code @RequirePermission} aspect.
     */
    public boolean currentUserHas(String permissionCode) {
        return currentUserPermissions().contains(permissionCode);
    }

    /**
     * True if the user holds ALL of the given permissions.
     */
    public boolean currentUserHasAll(String... codes) {
        Set<String> perms = currentUserPermissions();
        for (String c : codes) if (!perms.contains(c)) return false;
        return true;
    }

    /**
     * True if the user holds ANY of the given permissions.
     */
    public boolean currentUserHasAny(String... codes) {
        Set<String> perms = currentUserPermissions();
        for (String c : codes) if (perms.contains(c)) return true;
        return false;
    }

    private Set<String> permissionsFromLegacyRoleName(Long shopId, String roleName) {
        if (roleName == null || roleName.isBlank()) return Collections.emptySet();
        return roleRepository.findByShopIdAndName(shopId, roleName)
                .map(r -> r.getPermissions())
                .orElseGet(() -> {
                    log.debug("No role '{}' seeded for shop {} — falling back to empty permission set.", roleName, shopId);
                    return Collections.emptySet();
                });
    }

    private boolean isPlatformAdmin(Role role) {
        if (role == null) return false;
        return switch (role) {
            case SUPER_ADMIN, TECH_ADMIN, BILLING_ADMIN, SUPPORT_AGENT -> true;
            default -> false;
        };
    }
}
