package com.desitech.vyaparsathi.subscriptions.dto;

import com.desitech.vyaparsathi.subscriptions.enums.BillingCycle;
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
    private boolean premium;
    private long daysRemaining;
    private String lastUtr;
    private boolean usedTrial;
    private BillingCycle billingCycle;
    private Double amount;
    private Boolean canProcessSale;
    private boolean canStartTrial;
    // Monthly sales quota for the effective plan. `null` = unlimited.
    private Integer maxSalesPerMonth;
    // Sales completed this calendar month (excludes DRAFT rows).
    private long salesUsedThisMonth;
}