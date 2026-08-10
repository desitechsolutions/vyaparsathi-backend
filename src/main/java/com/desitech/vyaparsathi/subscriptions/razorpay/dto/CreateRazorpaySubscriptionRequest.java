package com.desitech.vyaparsathi.subscriptions.razorpay.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request body for {@code POST /api/subscriptions/razorpay/create}.
 *
 * <p>{@code shopId} is resolved from the authenticated JWT principal in the
 * controller and overrides any value sent by the client.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRazorpaySubscriptionRequest {

    /** Set server-side from the JWT principal — any client-supplied value is overridden. */
    private Long shopId;

    /** Matches VyaparSathi {@code Tier.name()} — e.g. STARTER, PRO, ENTERPRISE. */
    @NotBlank(message = "Plan code is required (e.g. STARTER, PRO, ENTERPRISE)")
    private String planCode;

    @Builder.Default
    private String billingCycle = "MONTHLY"; // MONTHLY | YEARLY

    /** Optional: pre-fill Razorpay checkout modal. */
    private String customerEmail;
    private String customerContact;
}
