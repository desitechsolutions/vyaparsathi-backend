package com.desitech.vyaparsathi.subscriptions.razorpay.controller;

import com.desitech.vyaparsathi.subscriptions.razorpay.service.RazorpayWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Public (unauthenticated) REST controller for Razorpay webhook events.
 *
 * <p>Base path: {@code /api/webhooks}
 *
 * <p><b>Why no auth?</b> Razorpay's servers send these POST requests directly —
 * they cannot send a JWT. The {@code /api/webhooks/**} path is excluded from
 * Spring Security JWT filter in {@code SecurityConfig}. Authentication is
 * entirely via HMAC-SHA256 signature verification in {@link RazorpayWebhookService}.
 *
 * <p><b>Raw body handling:</b> The {@link com.desitech.vyaparsathi.subscriptions.razorpay.config.RawPayloadCachingFilter}
 * wraps the request before this controller runs, allowing the body to be read as a
 * cached byte array via {@link ContentCachingRequestWrapper}. This is critical —
 * if the stream has already been consumed by JSON parsing, the HMAC cannot be verified.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Razorpay Webhooks", description = "Razorpay server-to-server event receiver (unauthenticated)")
public class RazorpayWebhookController {

    private final RazorpayWebhookService webhookService;

    @PostMapping("/razorpay")
    @Operation(summary = "Receive Razorpay webhook events")
    public ResponseEntity<Map<String, String>> handleWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        // ── 1. Extract raw body via cached wrapper ────────────────────────────
        String rawPayload;
        try {
            rawPayload = extractRawBody(request);
        } catch (IOException e) {
            log.error("[WEBHOOK-CTRL] Failed to read request body: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Could not read request body"));
        }

        if (rawPayload == null || rawPayload.isBlank()) {
            log.warn("[WEBHOOK-CTRL] Empty webhook body received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Empty request body"));
        }

        if (signature == null || signature.isBlank()) {
            log.warn("[WEBHOOK-CTRL] Missing X-Razorpay-Signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Missing X-Razorpay-Signature header"));
        }

        log.debug("[WEBHOOK-CTRL] Received webhook. Signature={}, BodyLength={}",
                signature.substring(0, Math.min(10, signature.length())) + "...",
                rawPayload.length());

        // ── 2. Delegate to service ────────────────────────────────────────────
        boolean processed = webhookService.processWebhook(rawPayload, signature);

        if (processed) {
            return ResponseEntity.ok(Map.of("status", "OK", "message", "Event received and processed"));
        } else {
            // Return 400 so Razorpay knows to retry (it stops retrying after N failures)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "REJECTED", "message", "Signature verification failed"));
        }
    }

    /**
     * Reads the raw request body from the {@link ContentCachingRequestWrapper}
     * (set by {@link com.desitech.vyaparsathi.subscriptions.razorpay.config.RawPayloadCachingFilter}).
     *
     * <p>If the request is not wrapped (e.g. in testing), falls back to reading
     * the input stream directly.
     */
    private String extractRawBody(HttpServletRequest request) throws IOException {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            // Force stream consumption so the wrapper populates its cache
            byte[] cached = wrapper.getContentAsByteArray();
            if (cached.length == 0) {
                // Stream not yet consumed — read it now
                cached = wrapper.getInputStream().readAllBytes();
            }
            return new String(cached, StandardCharsets.UTF_8);
        }
        // Fallback (should not happen in production with the filter active)
        return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
