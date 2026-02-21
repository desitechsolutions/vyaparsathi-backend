package com.desitech.vyaparsathi.shop.dto;

import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GlobalShopSummaryDTO {
    private Long shopId;
    private String shopName;
    private String shopCode;
    private String state;
    private LocalDateTime createdAt;
    private String ownerName;
    private String ownerEmail;
    private Boolean active;

    // Subscription Info
    private Tier currentTier;
    private SubscriptionStatus subscriptionStatus;
    private LocalDateTime expiryDate;
}