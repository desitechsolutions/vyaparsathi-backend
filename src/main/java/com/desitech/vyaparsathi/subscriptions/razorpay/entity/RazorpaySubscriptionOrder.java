package com.desitech.vyaparsathi.subscriptions.razorpay.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tracks every Razorpay subscription mandate created for a VyaparSathi shop.
 *
 * <p><b>Status lifecycle (mirrors Razorpay states):</b>
 * <pre>
 *   CREATED → AUTHENTICATED → ACTIVE → (PENDING → ACTIVE or HALTED)
 *   ACTIVE → PAUSED → ACTIVE
 *   ACTIVE / AUTHENTICATED → CANCELLED
 *   ACTIVE → COMPLETED
 *   ACTIVE → EXPIRED
 * </pre>
 *
 * <p>A shop must have at most one non-terminal subscription at a time.
 * {@code hasActiveSubscriptionForShop} enforces this constraint at the service layer.
 */
@Entity
@Table(name = "razorpay_subscription_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpaySubscriptionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    /** Matches VyaparSathi {@code Tier.name()} — e.g. STARTER, PRO, ENTERPRISE. */
    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    /** Razorpay Plan ID (e.g. {@code plan_XXXX}) auto-created on first use of this tier+cycle combo. */
    @Column(name = "razorpay_plan_id", nullable = false, length = 100)
    private String razorpayPlanId;

    /** Razorpay Subscription ID (e.g. {@code sub_XXXX}). Globally unique. */
    @Column(name = "razorpay_subscription_id", nullable = false, unique = true, length = 100)
    private String razorpaySubscriptionId;

    @Column(name = "razorpay_customer_id", nullable = false, length = 100)
    private String razorpayCustomerId;

    /**
     * Razorpay subscription status string.
     * Values: CREATED | AUTHENTICATED | ACTIVE | PENDING | PAUSED | HALTED | CANCELLED | COMPLETED | EXPIRED
     */
    @Column(nullable = false, length = 50)
    private String status;

    /** Mandate status. Values: PENDING | ACTIVE | HALTED | REJECTED | CANCELLED */
    @Column(name = "mandate_status", length = 50)
    private String mandateStatus;

    /** Billing frequency. Values: MONTHLY | YEARLY */
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private String billingCycle;

    /** Razorpay-generated short payment link for offline fallback. */
    @Column(name = "short_url")
    private String shortUrl;

    @Column(name = "current_start")
    private LocalDateTime currentStart;

    @Column(name = "current_end")
    private LocalDateTime currentEnd;

    @Column(name = "charge_at")
    private LocalDateTime chargeAt;

    @Column(name = "next_charge_at")
    private LocalDateTime nextChargeAt;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Column(name = "paid_count", nullable = false)
    private Integer paidCount;

    @Column(name = "remaining_count", nullable = false)
    private Integer remainingCount;

    @Column(name = "auth_attempts")
    private Integer authAttempts;

    @Column(name = "failed_retry_count")
    private Integer failedRetryCount;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /**
     * When {@code true}, Razorpay will cancel the subscription at the end of the
     * current billing cycle. We do NOT set {@code status = CANCELLED} immediately —
     * that transition is driven by the {@code subscription.cancelled} webhook event.
     */
    @Column(name = "cancel_at_cycle_end")
    private Boolean cancelAtCycleEnd;

    @Column(name = "last_webhook_event", length = 100)
    private String lastWebhookEvent;

    @Column(name = "last_webhook_at")
    private LocalDateTime lastWebhookAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (authAttempts == null) authAttempts = 0;
        if (failedRetryCount == null) failedRetryCount = 0;
        if (cancelAtCycleEnd == null) cancelAtCycleEnd = false;
        if (mandateStatus == null) mandateStatus = "PENDING";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
