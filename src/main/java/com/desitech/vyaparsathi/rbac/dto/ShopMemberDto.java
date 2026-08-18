package com.desitech.vyaparsathi.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Wire representation of one shop member — an active {@link
 * com.desitech.vyaparsathi.rbac.entity.UserShopMembership} row enriched
 * with the user's identity fields (name, email, avatar bits) so the FE
 * can render a members table without a follow-up lookup.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopMemberDto {
    private Long userId;
    private Long membershipId;
    private String username;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private boolean userActive;
    private boolean membershipActive;
    private boolean defaultMembership;
    private String role;
    private String roleDisplayName;
    private boolean mfaEnabled;
    private LocalDateTime lastLoginAt;
    private LocalDateTime joinedAt;
    private Long invitedBy;
}
