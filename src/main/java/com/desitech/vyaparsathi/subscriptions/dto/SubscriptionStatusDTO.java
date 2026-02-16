package com.desitech.vyaparsathi.subscriptions.dto;

import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatusDTO {
    private Tier tier;
    private SubscriptionStatus status;
    private boolean isPremium;
    private long daysRemaining;
    private String lastUtr;
}