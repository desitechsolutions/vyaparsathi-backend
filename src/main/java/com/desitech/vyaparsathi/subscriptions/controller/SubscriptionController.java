package com.desitech.vyaparsathi.subscriptions.controller;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.subscriptions.dto.*;
import com.desitech.vyaparsathi.subscriptions.entity.PaymentVerification;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionService subscriptionService;

    /**
     * Helper to extract Shop ID safely from the authenticated principal.
     */
    private Long getValidatedShopId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session not found");
        }
        User user = userDetails.getUser();
        if (user.getShop() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No shop associated with user: " + user.getUsername());
        }
        return user.getShop().getId();
    }

    /**
     * USER ENDPOINT: Start the 14-day free trial.
     * Returns the updated subscription status DTO so the frontend can react immediately.
     */
    @PostMapping("/trial/start")
    public ResponseEntity<SubscriptionStatusDTO> startTrial(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long shopId = getValidatedShopId(userDetails);
        logger.info("Trial activation requested for Shop ID: {}", shopId);

        // Initiate trial (service will validate existing state)
        subscriptionService.initiateTrial(shopId, Tier.STARTER);

        // Return the new subscription status so frontend can refresh UI instantly
        SubscriptionStatusDTO status = subscriptionService.getSubscriptionStatus(shopId);
        return ResponseEntity.ok(status);
    }

    /**
     * USER ENDPOINT: Submit UPI UTR for verification.
     * This is an asynchronous operation (bank verification) — return 202 Accepted with created id.
     */
    @PostMapping("/verify-payment")
    public ResponseEntity<?> submitUtr(
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long shopId = getValidatedShopId(userDetails);
        Long userId = userDetails.getUser().getId();

        // Basic validation
        if (request.getUtrNumber() == null || request.getUtrNumber().trim().length() < 6) {
            // length check: you can enforce exact 12 if needed
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UTR number");
        }
        if (request.getPlanTier() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan tier must be provided");
        }

        logger.info("UTR Submission: Shop {}, UTR {}, Tier {}, User {}", shopId, request.getUtrNumber(), request.getPlanTier(), userId);
        PaymentVerification created = subscriptionService.processUtrSubmission(userId, shopId, request);

        // Return verification id and status (PENDING)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "verificationId", created.getId(),
                "status", "PENDING",
                "message", "UTR submitted. We are verifying the payment in our HDFC account."
        ));
    }

    /**
     * USER ENDPOINT: Check current shop subscription status.
     * Used by the frontend to decide what to show the user.
     */
    @GetMapping("/status")
    public ResponseEntity<SubscriptionStatusDTO> getSubscriptionStatus(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long shopId = getValidatedShopId(userDetails);
        SubscriptionStatusDTO status = subscriptionService.getSubscriptionStatus(shopId);
        return ResponseEntity.ok(status);
    }

    /**
     * USER ENDPOINT: Get payment history for the logged-in shop.
     * Used for the "Transaction History" table in the Billing Dashboard.
     */
    @GetMapping("/my-payments")
    public ResponseEntity<List<PaymentVerificationDTO>> getMyPaymentHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long shopId = getValidatedShopId(userDetails);

        // You'll need to create a PaymentVerificationDTO and a service method
        // that finds all verifications by shopId ordered by date desc.
        List<PaymentVerificationDTO> history = subscriptionService.getShopPaymentHistory(shopId);
        return ResponseEntity.ok(history);
    }

    /**
     * USER ENDPOINT: Cancel subscription.
     * Usually sets a flag so the subscription doesn't auto-renew or
     * marks it as 'CANCEL_PENDING' until the end of the period.
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelSubscription(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long shopId = getValidatedShopId(userDetails);
        logger.warn("Subscription cancellation requested for Shop ID: {}", shopId);

        subscriptionService.cancelSubscription(shopId);

        return ResponseEntity.ok(Map.of(
                "message", "Subscription cancelled. You will have access until your current period ends."
        ));
    }

    // --- PLATFORM / ADMIN ENDPOINTS ---

    /**
     * SUPER_ADMIN: View queue of pending payments.
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/platform/pending")
    public ResponseEntity<List<PendingPaymentDTO>> getPendingVerifications() {
        List<PendingPaymentDTO> pending = subscriptionService.getAllPendingVerifications();
        return ResponseEntity.ok(pending);
    }

    /**
     * SUPER_ADMIN: Approve payment and activate plan.
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/platform/approve/{verificationId}")
    public ResponseEntity<?> approvePayment(
            @PathVariable Long verificationId,
            @AuthenticationPrincipal CustomUserDetails adminDetails) {

        if (adminDetails == null || adminDetails.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin session not found");
        }
        String adminUsername = adminDetails.getUser().getUsername();
        logger.info("Admin {} approving verificationId={}", adminUsername, verificationId);

        subscriptionService.activateSubscription(verificationId, adminUsername);
        return ResponseEntity.ok(Map.of("message", "Subscription successfully activated."));
    }

    /**
     * SUPER_ADMIN: Reject invalid UTRs.
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/platform/reject/{verificationId}")
    public ResponseEntity<?> rejectPayment(
            @PathVariable Long verificationId,
            @RequestParam(name = "reason", required = false) String reason,
            @AuthenticationPrincipal CustomUserDetails adminDetails) {

        if (adminDetails == null || adminDetails.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin session not found");
        }
        String adminUsername = adminDetails.getUser().getUsername();
        logger.info("Admin {} rejecting verificationId={} reason={}", adminUsername, verificationId, reason);

        subscriptionService.rejectSubscription(verificationId, reason);
        return ResponseEntity.ok(Map.of("message", "Payment rejected. Account remains expired/limited."));
    }

    /**
     * SUPER_ADMIN: Get global platform statistics for Tech Dashboard.
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/platform/stats")
    public ResponseEntity<PlatformStatsDTO> getPlatformStats() {
        return ResponseEntity.ok(subscriptionService.getPlatformStats());
    }

    /**
     * SUPER_ADMIN: Optional - Get revenue history for charts.
     * For now, returning an empty list so the frontend doesn't crash.
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/platform/revenue-history")
    public ResponseEntity<List<?>> getRevenueHistory(@RequestParam(defaultValue = "30") int days) {
        // Implement history logic later when needed
        return ResponseEntity.ok(List.of());
    }
}