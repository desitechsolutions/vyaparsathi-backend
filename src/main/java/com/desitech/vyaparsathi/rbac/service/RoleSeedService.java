package com.desitech.vyaparsathi.rbac.service;

import com.desitech.vyaparsathi.rbac.entity.Role;
import com.desitech.vyaparsathi.rbac.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;

/**
 * Service to seed and maintain system-preset roles and their permissions for shops.
 */
@Service
@RequiredArgsConstructor
public class RoleSeedService {

    private static final Logger log = LoggerFactory.getLogger(RoleSeedService.class);

    private final RoleRepository roleRepository;

    /**
     * Idempotently seeds or refreshes the system-preset roles for the specified shop.
     *
     * @param shopId the ID of the shop to seed roles for
     * @return the number of roles created or updated
     */
    @Transactional
    public int seedPresetRolesForShop(Long shopId) {
        if (shopId == null) {
            throw new IllegalArgumentException("shopId cannot be null");
        }
        int touched = 0;
        for (Map.Entry<String, SystemRoleDefinitions.RoleSpec> entry : SystemRoleDefinitions.PRESETS.entrySet()) {
            String name = entry.getKey();
            SystemRoleDefinitions.RoleSpec spec = entry.getValue();

            Role role = roleRepository.findByShopIdAndName(shopId, name).orElseGet(() -> {
                Role r = new Role();
                r.setShopId(shopId);
                r.setName(name);
                return r;
            });
            role.setDisplayName(spec.displayName());
            role.setDescription(spec.description());
            role.setSystem(true);
            role.setPermissions(new HashSet<>(spec.permissions()));
            roleRepository.save(role);
            touched++;
        }
        log.info("Seeded/refreshed {} preset roles for shopId={}", touched, shopId);
        return touched;
    }
}
