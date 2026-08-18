package com.desitech.vyaparsathi.rbac.controller;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.rbac.annotation.RequirePermission;
import com.desitech.vyaparsathi.rbac.entity.Permission;
import com.desitech.vyaparsathi.rbac.entity.Role;
import com.desitech.vyaparsathi.rbac.repository.PermissionRepository;
import com.desitech.vyaparsathi.rbac.repository.RoleRepository;
import com.desitech.vyaparsathi.rbac.service.CustomRoleService;
import com.desitech.vyaparsathi.rbac.service.SystemRoleDefinitions;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only view of the RBAC catalogue + roles seeded for the current
 * shop. Consumed by the FE's permission-matrix screen so the inviter can
 * see which permissions each role grants before they pick one.
 *
 *   GET /api/rbac/permissions  — the canonical permission catalogue
 *   GET /api/rbac/roles        — every role seeded for the current shop
 *                                (system-preset + custom)
 *
 * Write endpoints for custom roles are deferred to Phase 5F.4 (FE
 * permission-matrix editor); the seed runner already produces every
 * system-preset row that a shop needs to start operating.
 */
@RestController
@RequestMapping("/api/rbac")
@RequiredArgsConstructor
public class RoleAdminController {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final CustomRoleService customRoleService;

    /**
     * Full permission catalogue with module grouping.
     * TEAM_VIEW gates access — everyone who can see the team screen can
     * also see what the roles do.
     */
    @GetMapping("/permissions")
    @RequirePermission(value = {"TEAM_VIEW", "ROLE_MANAGE"}, mode = RequirePermission.Mode.ANY)
    public List<Map<String, String>> listPermissions() {
        List<Permission> all = permissionRepository.findAllByOrderByModuleAscCodeAsc();
        List<Map<String, String>> out = new ArrayList<>(all.size());
        for (Permission p : all) {
            out.add(Map.of(
                    "code", p.getCode(),
                    "module", p.getModule(),
                    "description", p.getDescription()
            ));
        }
        return out;
    }

    /**
     * Roles seeded for the current shop, ordered by preset order (OWNER
     * first, ADMIN, MANAGER, ACCOUNTANT, CASHIER, VIEWER, STAFF) and
     * custom roles after.
     */
    @GetMapping("/roles")
    @RequirePermission(value = {"TEAM_VIEW", "ROLE_MANAGE"}, mode = RequirePermission.Mode.ANY)
    public List<Map<String, Object>> listRoles() {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) return List.of();
        List<Role> roles = roleRepository.findByShopId(shopId);
        roles.sort((a, b) -> {
            int oa = SystemRoleDefinitions.orderOf(a.getName());
            int ob = SystemRoleDefinitions.orderOf(b.getName());
            if (oa != ob) return Integer.compare(oa, ob);
            return a.getName().compareToIgnoreCase(b.getName());
        });
        List<Map<String, Object>> out = new ArrayList<>(roles.size());
        for (Role r : roles) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("name", r.getName());
            row.put("displayName", r.getDisplayName());
            row.put("description", r.getDescription());
            row.put("system", r.isSystem());
            row.put("permissions", (Set<String>) r.getPermissions());
            out.add(row);
        }
        return out;
    }

    // ─── Write: custom roles ────────────────────────────────────────────

    @PostMapping("/roles")
    @RequirePermission("ROLE_MANAGE")
    public Map<String, Object> createRole(@RequestBody RoleWriteRequest req) {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) throw new IllegalStateException("No active shop context.");
        Role created = customRoleService.create(
                shopId, req.getName(), req.getDisplayName(), req.getDescription(),
                req.getPermissions() != null ? req.getPermissions() : new HashSet<>());
        return roleToMap(created);
    }

    @PutMapping("/roles/{id}")
    @RequirePermission("ROLE_MANAGE")
    public Map<String, Object> updateRole(@PathVariable("id") Long id, @RequestBody RoleWriteRequest req) {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) throw new IllegalStateException("No active shop context.");
        Role updated = customRoleService.update(
                shopId, id, req.getDisplayName(), req.getDescription(), req.getPermissions());
        return roleToMap(updated);
    }

    @DeleteMapping("/roles/{id}")
    @RequirePermission("ROLE_MANAGE")
    public ApiResponse<Void> deleteRole(@PathVariable("id") Long id) {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) throw new IllegalStateException("No active shop context.");
        customRoleService.delete(shopId, id);
        return new ApiResponse<>("success", "Role deleted.", null);
    }

    private Map<String, Object> roleToMap(Role r) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", r.getId());
        row.put("name", r.getName());
        row.put("displayName", r.getDisplayName());
        row.put("description", r.getDescription());
        row.put("system", r.isSystem());
        row.put("permissions", r.getPermissions());
        return row;
    }

    @Data
    public static class RoleWriteRequest {
        /** Required only on create; ignored on update (name is immutable). */
        @NotBlank
        private String name;
        private String displayName;
        private String description;
        private Set<String> permissions;
    }
}
