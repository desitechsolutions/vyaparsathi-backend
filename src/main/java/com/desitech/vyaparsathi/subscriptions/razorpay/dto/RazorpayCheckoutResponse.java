package com.desitech.vyaparsathi.subscriptions.razorpay.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Response returned to the frontend after a successful subscription order creation.
 *
 * <p>The frontend uses {@code keyId} and {@code razorpaySubscriptionId} to open
 * the Razorpay Standard Checkout modal for mandate authentication.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayCheckoutResponse {

    /** Razorpay publishable key-id — safe to expose to the frontend. */
    private String keyId;

    /** Razorpay subscription ID (e.g. {@code sub_XXXX}). Passed as {@code subscription_id} to the SDK. */
    private String razorpaySubscriptionId;

    private String razorpayCustomerId;
    private String razorpayPlanId;

    /** VyaparSathi Tier name (STARTER / PRO / ENTERPRISE). */
    private String planCode;

    private String billingCycle;

    /** Amount in INR (full rupees, not paise). For display purposes only. */
    private BigDecimal amount;

    private String currency;

    /** Current status of the subscription order (typically CREATED at this point). */
    private String status;

    /** Razorpay-generated short payment URL (fallback for offline checkout). */
    private String shortUrl;
}
