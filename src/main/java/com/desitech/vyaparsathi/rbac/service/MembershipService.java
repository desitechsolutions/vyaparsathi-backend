package com.desitech.vyaparsathi.rbac.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.rbac.entity.Role;
import com.desitech.vyaparsathi.rbac.entity.UserShopMembership;
import com.desitech.vyaparsathi.rbac.repository.RoleRepository;
import com.desitech.vyaparsathi.rbac.repository.UserShopMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Read/write API for {@link UserShopMembership}. Every mutation goes
 * through here so we can attach audit + validation in one place.
 */
@Service
@RequiredArgsConstructor
public class MembershipService {

    private static final Logger log = LoggerFactory.getLogger(MembershipService.class);

    private final UserShopMembershipRepository repository;
    private final RoleRepository roleRepository;

    // ─── Reads ───────────────────────────────────────────────────────────

    public List<UserShopMembership> listActiveForUser(Long userId) {
        return repository.findByUserIdAndActiveTrue(userId);
    }

    public List<UserShopMembership> listActiveForShop(Long shopId) {
        return repository.findByShopIdAndActiveTrue(shopId);
    }

    public Optional<UserShopMembership> find(Long userId, Long shopId) {
        return repository.findByUserIdAndShopId(userId, shopId);
    }

    public boolean isMemberOf(Long userId, Long shopId) {
        return repository.findByUserIdAndShopId(userId, shopId)
                .map(UserShopMembership::isActive)
                .orElse(false);
    }

    // ─── Writes ──────────────────────────────────────────────────────────

    /**
     * Idempotent add — creates a membership if none exists, reactivates
     * (and re-roles) an existing inactive row otherwise. Returns the
     * resulting membership.
     *
     * @param user       user being added
     * @param shopId     target shop
     * @param roleName   name of the role (must already exist for the shop; caller ensures presets are seeded)
     * @param invitedBy  optional inviter userId — recorded for audit
     * @param makeDefault true iff this should become the user's default shop
     */
    @Transactional
    public UserShopMembership addOrReactivate(User user, Long shopId, String roleName, Long invitedBy, boolean makeDefault) {
        if (user == null || shopId == null || roleName == null) {
            throw new IllegalArgumentException("user, shopId and roleName are required.");
        }

        Optional<Role> role = roleRepository.findByShopIdAndName(shopId, roleName);
        Long roleId = role.map(Role::getId).orElse(null);
        if (roleId == null) {
            log.warn("addOrReactivate: no role '{}' exists in shop {} — membership will fall back to legacy resolver.", roleName, shopId);
        }

        UserShopMembership mem = repository.findByUserIdAndShopId(user.getId(), shopId)
                .orElseGet(() -> {
                    UserShopMembership m = new UserShopMembership();
                    m.setUserId(user.getId());
                    m.setShopId(shopId);
                    m.setJoinedAt(LocalDateTime.now());
                    return m;
                });
        mem.setRole(roleName);
        mem.setRoleId(roleId);
        mem.setActive(true);
        if (invitedBy != null) mem.setInvitedBy(invitedBy);

        if (makeDefault) {
            // Clear any other default first — one default per user.
            List<UserShopMembership> existing = repository.findByUserId(user.getId());
            for (UserShopMembership other : existing) {
                if (!other.getShopId().equals(shopId) && other.isDefaultMembership()) {
                    other.setDefaultMembership(false);
                    repository.save(other);
                }
            }
            mem.setDefaultMembership(true);
        }
        return repository.save(mem);
    }

    /**
     * Deactivate (soft-remove) a membership. The row is kept so historical
     * references (invited_by, audit rows citing the user) still resolve.
     * Deactivating the default membership triggers a fallback: another
     * active membership (if any) becomes default.
     */
    @Transactional
    public void deactivate(Long userId, Long shopId) {
        UserShopMembership mem = repository.findByUserIdAndShopId(userId, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found."));
        mem.setActive(false);
        boolean wasDefault = mem.isDefaultMembership();
        mem.setDefaultMembership(false);
        repository.save(mem);

        if (wasDefault) {
            repository.findByUserIdAndActiveTrue(userId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setDefaultMembership(true);
                        repository.save(next);
                    });
        }
    }

    /**
     * Change the role for an existing active membership.
     */
    @Transactional
    public UserShopMembership changeRole(Long userId, Long shopId, String newRoleName) {
        UserShopMembership mem = repository.findByUserIdAndShopId(userId, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found."));
        if (!mem.isActive()) throw new IllegalStateException("Membership is inactive.");
        Long newRoleId = roleRepository.findByShopIdAndName(shopId, newRoleName)
                .map(Role::getId)
                .orElse(null);
        mem.setRole(newRoleName);
        mem.setRoleId(newRoleId);
        return repository.save(mem);
    }
}
