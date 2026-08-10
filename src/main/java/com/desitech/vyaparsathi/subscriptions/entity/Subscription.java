package com.desitech.vyaparsathi.subscriptions.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.subscriptions.enums.BillingCycle;
import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
public class Subscription extends ShopAwareEntity {

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Tier tier;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private LocalDateTime trialEndDate;

    @Column(length = 12)
    private String lastUtr;

    private Long lastUpdatedByUserId;

    @Column(name = "used_trial")
    private boolean usedTrial = false;

    @Enumerated(EnumType.STRING)
    private BillingCycle billingCycle;

    private Integer lastUpgradeBonusDays = 0;
    private Tier previousTier;

    public Tier getTier() { return tier; }
    public void setTier(Tier tier) { this.tier = tier; }

    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }

    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }

    public LocalDateTime getTrialEndDate() { return trialEndDate; }
    public void setTrialEndDate(LocalDateTime trialEndDate) { this.trialEndDate = trialEndDate; }

    public String getLastUtr() { return lastUtr; }
    public void setLastUtr(String lastUtr) { this.lastUtr = lastUtr; }

    public Long getLastUpdatedByUserId() { return lastUpdatedByUserId; }
    public void setLastUpdatedByUserId(Long lastUpdatedByUserId) { this.lastUpdatedByUserId = lastUpdatedByUserId; }

    public boolean isUsedTrial() { return usedTrial; }
    public void setUsedTrial(boolean usedTrial) { this.usedTrial = usedTrial; }

    public BillingCycle getBillingCycle() { return billingCycle; }
    public void setBillingCycle(BillingCycle billingCycle) { this.billingCycle = billingCycle; }

    public Integer getLastUpgradeBonusDays() { return lastUpgradeBonusDays; }
    public void setLastUpgradeBonusDays(Integer lastUpgradeBonusDays) { this.lastUpgradeBonusDays = lastUpgradeBonusDays; }

    public Tier getPreviousTier() { return previousTier; }
    public void setPreviousTier(Tier previousTier) { this.previousTier = previousTier; }
}