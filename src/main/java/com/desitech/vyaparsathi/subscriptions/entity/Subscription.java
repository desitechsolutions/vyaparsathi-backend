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
}