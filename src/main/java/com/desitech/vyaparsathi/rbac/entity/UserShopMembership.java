package com.desitech.vyaparsathi.rbac.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One user can belong to many shops with a different role in each.
 * Backfilled from {@code users.shop_id} on V110; new memberships are
 * created via {@link com.desitech.vyaparsathi.rbac.service.MembershipService}
 * (usually as a side-effect of accepting a {@code ShopInvitation}).
 *
 * <p>{@code users.shop_id} stays populated as the user's default /
 * primary shop so legacy paths keep working. Shop switching just
 * re-issues the JWT with a different {@code shopId} claim — the
 * primary-shop pointer is not touched by a switch.
 */
@Entity
@Table(name = "user_shop_membership")
@Getter
@Setter
@NoArgsConstructor
public class UserShopMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "shop_id", nullable = false, updatable = false)
    private Long shopId;

    /** Legacy role name for backwards compat — {@code OWNER}, {@code ADMIN}, {@code STAFF}, or any preset from Phase 5A. */
    @Column(nullable = false, length = 32)
    private String role;

    /**
     * Link to the new {@link Role} row for this shop. Nullable during the
     * transition — resolver falls back to the legacy {@code role} name to
     * find the matching preset when this is null.
     */
    @Column(name = "role_id")
    private Long roleId;

    @Column(nullable = false)
    private boolean active = true;

    /** True for one membership per user (the one users.shop_id points at). */
    @Column(name = "is_default", nullable = false)
    private boolean defaultMembership = false;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    /** userId of the inviter — nullable for OWNER rows backfilled by V110. */
    @Column(name = "invited_by")
    private Long invitedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
