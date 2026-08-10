package com.desitech.vyaparsathi.subscriptions.razorpay.util;

import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Stateless utility class for Razorpay cryptographic signature verification.
 *
 * <p>Two verification modes:
 * <ol>
 *   <li><b>Checkout signature</b> — verifies the HMAC-SHA256 over
 *       {@code razorpay_payment_id + "|" + razorpay_subscription_id} using the
 *       API key secret. Called after the user completes mandate authentication.</li>
 *   <li><b>Webhook signature</b> — delegates to the official Razorpay Java SDK
 *       {@code Utils.verifyWebhookSignature} which internally verifies HMAC-SHA256
 *       over the raw request body using the webhook secret.</li>
 * </ol>
 *
 * <p>Both comparisons use {@link MessageDigest#isEqual} (constant-time) to prevent
 * timing side-channel attacks.
 */
@Slf4j
public final class RazorpaySignatureUtil {

    private RazorpaySignatureUtil() {
        // Utility class — no instances
    }

    /**
     * Verifies the signature returned by the Razorpay checkout SDK in the
     * {@code handler} callback for subscription payments.
     *
     * <p>Signature format (per Razorpay docs):
     * <pre>HMAC-SHA256(razorpay_payment_id + "|" + razorpay_subscription_id, key_secret)</pre>
     *
     * @param paymentId      {@code response.razorpay_payment_id} from the SDK callback
     * @param subscriptionId {@code response.razorpay_subscription_id} from the SDK callback
     * @param signature      {@code response.razorpay_signature} from the SDK callback
     * @param secret         Razorpay API key secret (never the webhook secret)
     * @return {@code true} if signature is valid
     */
    public static boolean verifyCheckoutSignature(String paymentId,
                                                  String subscriptionId,
                                                  String signature,
                                                  String secret) {
        if (paymentId == null || subscriptionId == null || signature == null || secret == null) {
            log.warn("[RAZORPAY] verifyCheckoutSignature: one or more parameters are null");
            return false;
        }
        try {
            String data = paymentId + "|" + subscriptionId;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String generatedSignature = Hex.encodeHexString(hash);

            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    generatedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("[RAZORPAY] Checkout signature verification exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifies the {@code X-Razorpay-Signature} header on incoming webhook requests.
     *
     * <p>Delegates to the official Razorpay Java SDK {@code Utils.verifyWebhookSignature}
     * which performs HMAC-SHA256 over the raw payload body using the webhook secret.
     *
     * @param rawPayload full raw request body as a UTF-8 string (must NOT be parsed/prettified)
     * @param signature  value of the {@code X-Razorpay-Signature} HTTP header
     * @param secret     Razorpay webhook secret (different from the API key secret)
     * @return {@code true} if the signature is valid
     */
    public static boolean verifyWebhookSignature(String rawPayload, String signature, String secret) {
        if (rawPayload == null || signature == null || secret == null) {
            log.warn("[RAZORPAY] verifyWebhookSignature: one or more parameters are null");
            return false;
        }
        try {
            return Utils.verifyWebhookSignature(rawPayload, signature, secret);
        } catch (RazorpayException e) {
            log.error("[RAZORPAY] Webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }
}
