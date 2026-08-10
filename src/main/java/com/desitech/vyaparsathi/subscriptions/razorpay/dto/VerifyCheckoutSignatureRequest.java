package com.desitech.vyaparsathi.subscriptions.razorpay.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request body for {@code POST /api/subscriptions/razorpay/verify-checkout-signature}.
 *
 * <p>The three {@code razorpay_*} fields are returned by the Razorpay JavaScript
 * SDK in the {@code handler} callback after the user completes mandate authentication.
 * The backend verifies the HMAC-SHA256 signature before activating the subscription.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyCheckoutSignatureRequest {

    /** Set server-side from the JWT principal — any client value is overridden. */
    private Long shopId;

    @NotBlank(message = "razorpay_payment_id is required")
    private String razorpay_payment_id;

    @NotBlank(message = "razorpay_subscription_id is required")
    private String razorpay_subscription_id;

    @NotBlank(message = "razorpay_signature is required")
    private String razorpay_signature;
}
