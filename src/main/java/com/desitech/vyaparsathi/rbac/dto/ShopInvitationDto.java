package com.desitech.vyaparsathi.rbac.dto;

import com.desitech.vyaparsathi.rbac.entity.ShopInvitation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Wire representation of a {@link ShopInvitation}. Deliberately does NOT
 * include the token hash — the token is emailed once and never surfaced
 * on any read endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopInvitationDto {
    private Long id;
    private Long shopId;
    private String shopName;
    private String email;
    private String phone;
    private String roleName;
    private String status;
    private String inviterName;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime revokedAt;
    private String message;
}
