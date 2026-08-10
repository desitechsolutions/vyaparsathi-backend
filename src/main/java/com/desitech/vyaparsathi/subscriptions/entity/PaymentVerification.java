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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getUtrNumber() { return utrNumber; }
    public void setUtrNumber(String utrNumber) { this.utrNumber = utrNumber; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Tier getPlanRequested() { return planRequested; }
    public void setPlanRequested(Tier planRequested) { this.planRequested = planRequested; }

    public PaymentVerificationStatus getStatus() { return status; }
    public void setStatus(PaymentVerificationStatus status) { this.status = status; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }

    public BillingCycle getBillingCycle() { return billingCycle; }
    public void setBillingCycle(BillingCycle billingCycle) { this.billingCycle = billingCycle; }
}