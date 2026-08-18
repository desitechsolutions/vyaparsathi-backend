package com.desitech.vyaparsathi.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compact view of one shop the current user belongs to. Used by the
 * shop-switcher dropdown and account-profile shop list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopMembershipDto {
    private Long shopId;
    private String shopName;
    private String shopCode;
    private String industryType;
    private String logoPath;
    private String role;              // role name (e.g. OWNER, MANAGER)
    private String roleDisplayName;   // human-friendly ("Manager")
    private boolean active;
    private boolean isDefault;
}
