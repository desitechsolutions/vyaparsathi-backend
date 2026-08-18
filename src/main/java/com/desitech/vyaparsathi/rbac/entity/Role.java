package com.desitech.vyaparsathi.rbac.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A role within a specific shop. Two flavours:
 *
 * <ul>
 *   <li><b>System-preset</b> — {@code is_system=TRUE}. Seeded for every
 *       shop by {@code RoleSeedRunner}: OWNER, ADMIN, MANAGER,
 *       ACCOUNTANT, CASHIER, VIEWER, STAFF. The permission set of a
 *       system-preset role is defined in code and refreshed on every
 *       boot — the UI shows them read-only.</li>
 *   <li><b>Shop-custom</b> — {@code is_system=FALSE}. Created by shop
 *       OWNER/ADMIN via the role manager; permission set fully editable.</li>
 * </ul>
 *
 * <p>Shop-scoped by design: two shops can each have a "Manager" role
 * that grants different permissions.
 *
 * <p>Not tenant-filtered through {@code ShopFilterAspect} because we
 * always look up by (shopId, name) or (id) explicitly, and role setup
 * happens off the normal request path.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning shop. Nullable to allow future platform-level roles
     * (SUPER_ADMIN, TECH_ADMIN) to live here without a shop scope; today
     * every seeded row has a shop_id.
     */
    @Column(name = "shop_id")
    private Long shopId;

    /** Machine name — {@code OWNER}, {@code MANAGER}, {@code ACCOUNTANT}, or a custom slug. */
    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(length = 255)
    private String description;

    /** System-preset roles are seeded, refreshed on boot, and read-only in the UI. */
    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * Permission codes granted to this role. Stored via {@code role_permissions}
     * as a plain string collection — mapping to the full {@link Permission}
     * entity is unnecessary since we only need the codes at check-time.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id")
    )
    @Column(name = "permission_code", length = 64, nullable = false)
    private Set<String> permissions = new HashSet<>();

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
