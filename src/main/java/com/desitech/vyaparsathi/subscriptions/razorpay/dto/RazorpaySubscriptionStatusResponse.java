package com.desitech.vyaparsathi.subscriptions.razorpay.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Aggregated subscription status response combining data from both the core
 * {@code Subscription} entity and the {@code RazorpaySubscriptionOrder} entity.
 *
 * <p>The frontend uses this to render the AutoPay status card, billing cycle info,
 * and next-charge countdown.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpaySubscriptionStatusResponse {

    private Long shopId;

    /** RAZORPAY | MANUAL — informs the frontend which payment mode is active. */
    private String provider;

    /** VyaparSathi Tier name of the active subscription (FREE / STARTER / PRO / ENTERPRISE). */
    private String planCode;

    /** {@code true} if the shop currently has an active, non-expired subscription. */
    private Boolean active;

    /** When the current subscription period ends (matches {@code Subscription.endDate}). */
    private LocalDateTime validTill;

    /** Razorpay subscription ID (e.g. {@code sub_XXXX}). */
    private String razorpaySubscriptionId;

    private String razorpayCustomerId;

    /** Razorpay mandate status string. Values: CREATED | AUTHENTICATED | ACTIVE | PAUSED | HALTED | CANCELLED | COMPLETED | EXPIRED | NONE */
    private String status;

    /** Values: PENDING | ACTIVE | HALTED | REJECTED | CANCELLED | NONE */
    private String mandateStatus;

    private String billingCycle;

    private LocalDateTime nextChargeAt;

    private Integer paidCount;

    private Integer remainingCount;

    /**
     * When {@code true} the subscription will be cancelled at end of the current
     * billing cycle but access is still active.
     */
    private Boolean cancelAtCycleEnd;

    /** Current plan price in INR. For display purposes only. */
    private BigDecimal priceAmount;

    private String shortUrl;
}
