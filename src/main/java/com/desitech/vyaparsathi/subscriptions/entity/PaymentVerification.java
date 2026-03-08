package com.desitech.vyaparsathi.subscriptions.entity;

import com.desitech.vyaparsathi.subscriptions.enums.BillingCycle;
import com.desitech.vyaparsathi.subscriptions.enums.PaymentVerificationStatus;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_verifications")
@Getter
@Setter
public class PaymentVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long shopId;

    @Column(nullable = false, unique = true, length = 12)
    private String utrNumber;

    private Double amount;

    @Enumerated(EnumType.STRING)
    private Tier planRequested;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentVerificationStatus status;

    private LocalDateTime submittedAt = LocalDateTime.now();

    private LocalDateTime verifiedAt;

    private String verifiedBy;

    @Enumerated(EnumType.STRING)
    private BillingCycle billingCycle;
}