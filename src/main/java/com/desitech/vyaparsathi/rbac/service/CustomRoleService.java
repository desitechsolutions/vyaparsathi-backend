package com.desitech.vyaparsathi.rbac.service;

import com.desitech.vyaparsathi.rbac.entity.Permission;
import com.desitech.vyaparsathi.rbac.entity.Role;
import com.desitech.vyaparsathi.rbac.entity.UserShopMembership;
import com.desitech.vyaparsathi.rbac.repository.PermissionRepository;
import com.desitech.vyaparsathi.rbac.repository.RoleRepository;
import com.desitech.vyaparsathi.rbac.repository.UserShopMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Create / edit / delete shop-custom roles. System-preset roles are
 * refused by every mutating method — they're seeded and maintained by
 * {@link RoleSeedRunner}.
 *
 * <p>Delete is safe: it refuses to remove a role while any user holds
 * a {@link UserShopMembership} that references it. Callers must first
 * reassign those members to a different role.
 */
@Service
@RequiredArgsConstructor
public class CustomRoleService {

    private static final Logger log = LoggerFactory.getLogger(CustomRoleService.class);

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserShopMembershipRepository membershipRepository;

    /**
     * Create a new custom role for the given shop. Name is normalised to
     * uppercase / underscored so it slots into the same lookup path the
     * legacy Role enum uses.
     */
    @Transactional
    public Role create(Long shopId, String name, String displayName, String description, Set<String> permissions) {
        if (shopId == null) throw new IllegalArgumentException("shopId is required.");
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Role name is required.");
        if (SystemRoleDefinitions.PRESETS.containsKey(normalized)) {
            throw new IllegalArgumentException(
                    "The name '" + normalized + "' is reserved for a system-preset role. Pick a different name.");
        }
        if (roleRepository.findByShopIdAndName(shopId, normalized).isPresent()) {
            throw new IllegalArgumentException("A role named '" + normalized + "' already exists in this shop.");
        }
        Set<String> cleanPerms = validatePermissions(permissions);

        Role role = new Role();
        role.setShopId(shopId);
        role.setName(normalized);
        role.setDisplayName((displayName != null && !displayName.isBlank()) ? displayName.trim() : normalized);
        role.setDescription(description);
        role.setSystem(false);
        role.setPermissions(cleanPerms);
        Role saved = roleRepository.save(role);
        log.info("Created custom role '{}' for shop {} with {} permissions", normalized, shopId, cleanPerms.size());
        return saved;
    }

    /**
     * Update a custom role. Refuses to touch system-preset roles or roles
     * belonging to a different shop than the caller expected.
     */
    @Transactional
    public Role update(Long shopId, Long roleId, String displayName, String description, Set<String> permissions) {
        Role role = loadEditableRole(shopId, roleId);
        if (displayName != null && !displayName.isBlank()) role.setDisplayName(displayName.trim());
        if (description != null) role.setDescription(description);
        if (permissions != null) role.setPermissions(validatePermissions(permissions));
        Role saved = roleRepository.save(role);
        log.info("Updated custom role '{}' (id={}) for shop {}", saved.getName(), saved.getId(), shopId);
        return saved;
    }

    /**
     * Delete a custom role. Blocked if any active membership still points
     * at it — the caller must reassign those members first.
     */
    @Transactional
    public void delete(Long shopId, Long roleId) {
        Role role = loadEditableRole(shopId, roleId);
        List<UserShopMembership> holders = membershipRepository.findByShopIdAndActiveTrue(shopId).stream()
                .filter(m -> roleId.equals(m.getRoleId()) || role.getName().equalsIgnoreCase(m.getRole()))
                .toList();
        if (!holders.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot delete role '" + role.getName() + "' — " + holders.size() +
                    " team member(s) still hold it. Reassign them first.");
        }
        roleRepository.delete(role);
        log.info("Deleted custom role '{}' (id={}) from shop {}", role.getName(), roleId, shopId);
    }

    // ─── Internals ───────────────────────────────────────────────────────

    private Role loadEditableRole(Long shopId, Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found."));
        if (!shopId.equals(role.getShopId())) {
            throw new IllegalArgumentException("Role does not belong to the active shop.");
        }
        if (role.isSystem()) {
            throw new IllegalStateException("System-preset roles cannot be edited or deleted.");
        }
        return role;
    }

    /**
     * Filter a submitted permission set down to codes that actually exist
     * in the catalogue. Silently drops unknown codes rather than 400-ing
     * so a FE built against an older permission catalogue doesn't wedge
     * on a partial deploy.
     */
    private Set<String> validatePermissions(Set<String> raw) {
        if (raw == null || raw.isEmpty()) return new HashSet<>();
        Set<String> known = permissionRepository.findAll().stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());
        return raw.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .filter(known::contains)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String normalizeName(String name) {
        if (name == null) return "";
        return name.trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9_]", "")
                .toUpperCase();
    }
}
