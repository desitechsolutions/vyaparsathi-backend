package com.desitech.vyaparsathi.subscriptions.razorpay.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Razorpay SDK.
 *
 * <p>Values are driven by environment variables so that no secret is ever
 * hard-coded in source control:
 * <pre>
 *   RAZORPAY_KEY_ID     → subscription.razorpay.key-id
 *   RAZORPAY_KEY_SECRET → subscription.razorpay.key-secret
 *   RAZORPAY_WEBHOOK_SECRET → subscription.razorpay.webhook-secret
 * </pre>
 */
@Configuration
@Slf4j
@Getter
public class RazorpayConfig {

    @Value("${subscription.provider:RAZORPAY}")
    private String provider;

    @Value("${subscription.razorpay.key-id:}")
    private String keyId;

    @Value("${subscription.razorpay.key-secret:}")
    private String keySecret;

    @Value("${subscription.razorpay.webhook-secret:}")
    private String webhookSecret;

    @Value("${subscription.razorpay.currency:INR}")
    private String currency;

    @Bean
    public RazorpayClient razorpayClient() throws RazorpayException {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new IllegalStateException(
                    "Razorpay credentials are not configured. Please supply RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET as environment variables or application properties.");
        }
        log.info("[RAZORPAY] Initialising RazorpayClient bean with Key ID: {}", keyId);
        return new RazorpayClient(keyId, keySecret);
    }
}
