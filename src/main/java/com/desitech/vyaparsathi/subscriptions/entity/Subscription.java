package com.desitech.vyaparsathi.subscriptions.entity;

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
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false, unique = true)
    private Shop shop;

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
}