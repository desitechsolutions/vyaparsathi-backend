package com.desitech.vyaparsathi.subscriptions.razorpay.controller;

import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.subscriptions.razorpay.dto.CreateRazorpaySubscriptionRequest;
import com.desitech.vyaparsathi.subscriptions.razorpay.dto.RazorpayCheckoutResponse;
import com.desitech.vyaparsathi.subscriptions.razorpay.dto.RazorpaySubscriptionStatusResponse;
import com.desitech.vyaparsathi.subscriptions.razorpay.dto.VerifyCheckoutSignatureRequest;
import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpayPaymentLog;
import com.desitech.vyaparsathi.subscriptions.razorpay.service.RazorpaySubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Razorpay AutoPay subscription management.
 *
 * <p>Base path: {@code /api/subscriptions/razorpay}
 *
 * <p>The {@code shopId} is always resolved from the authenticated JWT principal to
 * prevent cross-tenant data access — any {@code shopId} in the request body is
 * overridden with the value from the token.
 */
@RestController
@RequestMapping("/api/subscriptions/razorpay")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Razorpay Subscriptions", description = "Razorpay AutoPay mandate management endpoints")
public class RazorpaySubscriptionController {

    private final RazorpaySubscriptionService subscriptionService;

    // ───────────────────────────────────────────────────────────────────────────
    //  CREATE
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Creates a Razorpay subscription order for the authenticated shop.
     * Returns the checkout details needed by the frontend to open the mandate modal.
     */
    @PostMapping("/create")
    @Operation(summary = "Create Razorpay subscription order")
    public ResponseEntity<RazorpayCheckoutResponse> createSubscription(
            @Valid @RequestBody CreateRazorpaySubscriptionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long shopId = resolveShopId(userDetails);
        request.setShopId(shopId); // Override any client-supplied shopId for security
        log.info("[RAZORPAY-CTRL] create subscription: shopId={}, plan={}, cycle={}",
                shopId, request.getPlanCode(), request.getBillingCycle());
        return ResponseEntity.ok(subscriptionService.createSubscriptionOrder(request));
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  VERIFY CHECKOUT SIGNATURE
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Verifies the HMAC-SHA256 signature from the Razorpay SDK callback after
     * the user completes mandate authentication in the checkout modal.
     */
    @PostMapping("/verify-checkout-signature")
    @Operation(summary = "Verify Razorpay checkout signature after mandate authentication")
    public ResponseEntity<Map<String, String>> verifyCheckoutSignature(
            @Valid @RequestBody VerifyCheckoutSignatureRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long shopId = resolveShopId(userDetails);
        request.setShopId(shopId);
        log.info("[RAZORPAY-CTRL] verify-checkout: shopId={}, payId={}, subId={}",
                shopId, request.getRazorpay_payment_id(), request.getRazorpay_subscription_id());

        boolean ok = subscriptionService.verifyAndActivateCheckout(
                shopId,
                request.getRazorpay_payment_id(),
                request.getRazorpay_subscription_id(),
                request.getRazorpay_signature()
        );

        if (ok) {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Mandate authenticated successfully."));
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "FAILED", "message", "Signature verification failed. Please contact support."));
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  STATUS
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Returns the aggregated subscription status for the authenticated shop.
     * Admin users may optionally pass {@code ?shopId=X} to query any shop.
     */
    @GetMapping("/status")
    @Operation(summary = "Get Razorpay subscription status")
    public ResponseEntity<RazorpaySubscriptionStatusResponse> getStatus(
            @RequestParam(required = false) Long shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long effectiveShopId = resolveShopIdWithAdminOverride(shopId, userDetails);
        return ResponseEntity.ok(subscriptionService.getSubscriptionStatus(effectiveShopId));
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  PAUSE
    // ───────────────────────────────────────────────────────────────────────────

    @PostMapping("/pause")
    @Operation(summary = "Pause the Razorpay AutoPay mandate")
    public ResponseEntity<Map<String, String>> pause(
            @RequestParam(required = false) Long shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long effectiveShopId = resolveShopIdWithAdminOverride(shopId, userDetails);
        log.info("[RAZORPAY-CTRL] pause: shopId={}", effectiveShopId);
        subscriptionService.pauseSubscription(effectiveShopId);
        return ResponseEntity.ok(Map.of("status", "PAUSED", "message", "AutoPay mandate paused successfully."));
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  RESUME
    // ───────────────────────────────────────────────────────────────────────────

    @PostMapping("/resume")
    @Operation(summary = "Resume a paused Razorpay AutoPay mandate")
    public ResponseEntity<Map<String, String>> resume(
            @RequestParam(required = false) Long shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long effectiveShopId = resolveShopIdWithAdminOverride(shopId, userDetails);
        log.info("[RAZORPAY-CTRL] resume: shopId={}", effectiveShopId);
        subscriptionService.resumeSubscription(effectiveShopId);
        return ResponseEntity.ok(Map.of("status", "ACTIVE", "message", "AutoPay mandate resumed successfully."));
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  CANCEL
    // ───────────────────────────────────────────────────────────────────────────

    @PostMapping("/cancel")
    @Operation(summary = "Cancel the Razorpay AutoPay mandate")
    public ResponseEntity<Map<String, String>> cancel(
            @RequestParam(defaultValue = "true") boolean cancelAtCycleEnd,
            @RequestParam(required = false) Long shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long effectiveShopId = resolveShopIdWithAdminOverride(shopId, userDetails);
        log.info("[RAZORPAY-CTRL] cancel: shopId={}, atCycleEnd={}", effectiveShopId, cancelAtCycleEnd);
        subscriptionService.cancelSubscription(effectiveShopId, cancelAtCycleEnd);
        String msg = cancelAtCycleEnd
                ? "Subscription will be cancelled at end of the current billing cycle."
                : "Subscription cancelled immediately.";
        return ResponseEntity.ok(Map.of("status", "CANCELLATION_SCHEDULED", "message", msg));
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  INVOICES
    // ───────────────────────────────────────────────────────────────────────────

    @GetMapping("/invoices")
    @Operation(summary = "Fetch Razorpay payment log / invoice history")
    public ResponseEntity<List<RazorpayPaymentLog>> getInvoices(
            @RequestParam(required = false) Long shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long effectiveShopId = resolveShopIdWithAdminOverride(shopId, userDetails);
        return ResponseEntity.ok(subscriptionService.getPaymentLogs(effectiveShopId));
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  PRIVATE HELPERS
    // ───────────────────────────────────────────────────────────────────────────

    private Long resolveShopId(CustomUserDetails userDetails) {
        var shop = userDetails.getUser().getShop();
        if (shop == null || shop.getId() == null) {
            throw new IllegalStateException("Shop context missing from JWT. Please re-login.");
        }
        return shop.getId();
    }

    /**
     * Resolves shopId with admin override support.
     * SUPER_ADMIN callers may pass an explicit {@code shopId} query param to
     * manage subscriptions for any shop.
     */
    private Long resolveShopIdWithAdminOverride(Long requestedShopId, CustomUserDetails userDetails) {
        boolean isSuperAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        if (isSuperAdmin && requestedShopId != null) {
            return requestedShopId;
        }
        return resolveShopId(userDetails);
    }
}
