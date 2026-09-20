package com.desitech.vyaparsathi.rbac.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.rbac.entity.Permission;
import com.desitech.vyaparsathi.rbac.entity.Role;
import com.desitech.vyaparsathi.rbac.repository.PermissionRepository;
import com.desitech.vyaparsathi.rbac.repository.RoleRepository;
import com.desitech.vyaparsathi.rbac.repository.UserShopMembershipRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seeds the system-preset roles ({@link SystemRoleDefinitions#PRESETS})
 * for every shop on application boot. Also re-applies the canonical
 * permission set for each preset, so tweaking
 * {@code SystemRoleDefinitions} + redeploying is enough to update every
 * shop's presets — no runtime admin action required.
 *
 * <p>Custom (non-system) roles are never touched. New shops created
 * post-boot are seeded via {@link RoleSeedService#seedPresetRolesForShop}.
 */
@Component
@RequiredArgsConstructor
public class RoleSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleSeedRunner.class);

    private final ShopRepository shopRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final UserShopMembershipRepository membershipRepository;
    private final RoleSeedService roleSeedService;
    private final MembershipService membershipService;

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        ensurePermissionCatalogue();
        validate();
        int total = 0;
        List<Shop> shops = shopRepository.findAll();
        for (Shop shop : shops) {
            total += seedForShop(shop.getId());
        }
        log.info("RBAC seed complete — {} shops refreshed, {} role rows upserted.", shops.size(), total);

        // Backfill missing memberships for OWNER users whose shops were created before membership hook
        int backfilled = 0;
        List<User> owners = userRepository.findByRole(com.desitech.vyaparsathi.auth.model.Role.OWNER);
        for (User owner : owners) {
            if (owner.getShop() != null) {
                Long shopId = owner.getShop().getId();
                if (membershipRepository.findByUserIdAndShopId(owner.getId(), shopId).isEmpty()) {
                    try {
                        membershipService.addOrReactivate(owner, shopId, "OWNER", null, true);
                        backfilled++;
                        log.info("Backfilled missing OWNER membership for user={} in shopId={}", owner.getUsername(), shopId);
                    } catch (Exception e) {
                        log.warn("Could not backfill membership for user={} in shopId={}: {}", owner.getUsername(), shopId, e.getMessage());
                    }
                }
            }
        }
        if (backfilled > 0) {
            log.info("Backfilled {} missing OWNER memberships.", backfilled);
        }
    }

    /**
     * The Flyway migration V109 seeds the permissions catalogue in prod,
     * but Spring-Boot integration tests use Hibernate auto-DDL against
     * H2 which creates the empty table without running Flyway. Fill it
     * from Java when the table is empty so tests + prod both boot green.
     */
    private void ensurePermissionCatalogue() {
        if (permissionRepository.count() > 0) return;
        log.info("Permission catalogue is empty — seeding {} codes from SystemRoleDefinitions.",
                SystemRoleDefinitions.ALL_PERMISSIONS.size());
        for (String code : SystemRoleDefinitions.ALL_PERMISSIONS) {
            Permission p = new Permission();
            p.setCode(code);
            p.setModule(deriveModule(code));
            p.setDescription(deriveDescription(code));
            permissionRepository.save(p);
        }
    }

    /**
     * Coarse module bucket derived from the permission code's prefix.
     * Matches the manual grouping in V109; used only when we're
     * auto-seeding (test path).
     */
    private String deriveModule(String code) {
        String upper = code.toUpperCase();
        if (upper.startsWith("SALES_") || upper.startsWith("INVOICE_") || upper.startsWith("QUOTATION_")
                || upper.startsWith("SALES_ORDER_") || upper.startsWith("PROFORMA_")
                || upper.startsWith("CREDIT_NOTE_") || upper.startsWith("DELIVERY_CHALLAN_")) return "SALES";
        if (upper.startsWith("PURCHASE_") || upper.startsWith("PO_") || upper.startsWith("GRN_")
                || upper.startsWith("DEBIT_NOTE_")) return "PURCHASES";
        if (upper.startsWith("ITEM_") || upper.startsWith("STOCK_") || upper.startsWith("CATEGORY_")) return "INVENTORY";
        if (upper.startsWith("CUSTOMER_") || upper.startsWith("SUPPLIER_")) return "CONTACTS";
        if (upper.startsWith("PAYMENT_") || upper.startsWith("LEDGER_")
                || upper.startsWith("EXPENSE_") || upper.startsWith("BANK_ACCOUNT_")) return "ACCOUNTING";
        if (upper.startsWith("REPORTS_") || upper.startsWith("GST_REPORTS_")
                || upper.startsWith("AUDIT_LOG_") || upper.startsWith("COMPLIANCE_")) return "REPORTS";
        if (upper.startsWith("TEAM_") || upper.startsWith("ROLE_")) return "TEAM";
        if (upper.startsWith("SHOP_SETTINGS_") || upper.startsWith("BILLING_")) return "SETTINGS";
        return "OTHER";
    }

    private String deriveDescription(String code) {
        // Fallback description — humanised code. Prod paths use the DB
        // descriptions from V109.
        String[] parts = code.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            if (p.length() > 0) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    /**
     * Idempotent seeding for one shop. Returns the number of role rows
     * created or updated. Delegated to {@link RoleSeedService#seedPresetRolesForShop}.
     */
    @Transactional
    public int seedForShop(Long shopId) {
        return roleSeedService.seedPresetRolesForShop(shopId);
    }

    /**
     * Validate that every permission code referenced by a preset actually
     * exists in the {@code permissions} table. Catches typos in
     * {@link SystemRoleDefinitions} before they poison a shop's role setup.
     */
    private void validate() {
        Set<String> known = new HashSet<>();
        permissionRepository.findAll().forEach(p -> known.add(p.getCode()));
        for (Map.Entry<String, SystemRoleDefinitions.RoleSpec> entry : SystemRoleDefinitions.PRESETS.entrySet()) {
            for (String code : entry.getValue().permissions()) {
                if (!known.contains(code)) {
                    throw new IllegalStateException(
                            "SystemRoleDefinitions references unknown permission '" + code +
                            "' in role '" + entry.getKey() + "'. Add it to the permissions catalogue in V109 (or a follow-up migration).");
                }
            }
        }
    }
}
