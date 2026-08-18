package com.desitech.vyaparsathi.rbac.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Canonical catalogue entry for one permission code. Seeded by
 * {@code V109__rbac_permissions_roles.sql}; new codes are added in
 * subsequent migrations, never at runtime.
 *
 * <p>Not shop-scoped — the catalogue is global. Which permissions a
 * particular {@link Role} grants is per-shop and lives in
 * {@code role_permissions}.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Machine-readable code — {@code MODULE_ACTION}, e.g. {@code SALES_CREATE}. */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    /** Grouping label used by the permission-matrix UI (SALES / INVENTORY / …). */
    @Column(nullable = false, length = 32)
    private String module;

    @Column(nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
