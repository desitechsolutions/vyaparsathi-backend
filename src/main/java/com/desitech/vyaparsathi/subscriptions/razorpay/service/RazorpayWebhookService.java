package com.desitech.vyaparsathi.subscriptions.razorpay.service;

import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.subscriptions.entity.Subscription;
import com.desitech.vyaparsathi.subscriptions.enums.BillingCycle;
import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.razorpay.config.RazorpayConfig;
import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpayPaymentLog;
import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpaySubscriptionOrder;
import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpayWebhookEvent;
import com.desitech.vyaparsathi.subscriptions.razorpay.repository.RazorpayPaymentLogRepository;
import com.desitech.vyaparsathi.subscriptions.razorpay.repository.RazorpaySubscriptionOrderRepository;
import com.desitech.vyaparsathi.subscriptions.razorpay.repository.RazorpayWebhookEventRepository;
import com.desitech.vyaparsathi.subscriptions.razorpay.util.RazorpaySignatureUtil;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Processes incoming Razorpay webhook events for subscription lifecycle management.
 *
 * <p><b>Security architecture:</b>
 * <ol>
 *   <li>HMAC-SHA256 signature is verified first using the webhook secret.</li>
 *   <li>Event ID idempotency is checked second to prevent duplicate processing.</li>
 *   <li>Business logic is applied only after both checks pass.</li>
 * </ol>
 *
 * <p><b>Multi-tenancy / shop context:</b>
 * Webhook requests arrive without a user JWT. The shop context is resolved from
 * the {@code shopId} embedded in {@code notes.shopId} of the subscription order,
 * or by looking up the {@link RazorpaySubscriptionOrder} by its
 * {@code razorpaySubscriptionId}. We then load the shop explicitly for all DB writes.
 *
 * <p><b>Events handled:</b>
 * <ul>
 *   <li>{@code subscription.authenticated} — mandate authenticated, access NOT yet active</li>
 *   <li>{@code subscription.activated}    — subscription is live (rare; usually charged handles this)</li>
 *   <li>{@code subscription.charged}      — recurring payment succeeded; extends access window</li>
 *   <li>{@code subscription.pending}      — payment pending (bank delay)</li>
 *   <li>{@code subscription.halted}       — multiple payment failures; mandate suspended</li>
 *   <li>{@code subscription.paused}       — paused via API call</li>
 *   <li>{@code subscription.resumed}      — resumed via API call</li>
 *   <li>{@code subscription.cancelled}    — cancelled (may be end-of-cycle or immediate)</li>
 *   <li>{@code subscription.completed}    — all billing cycles fulfilled</li>
 *   <li>{@code subscription.expired}      — expired without completion</li>
 *   <li>{@code payment.failed}            — payment failure on a recurring charge attempt</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookService {

    private final RazorpayConfig razorpayConfig;
    private final RazorpayWebhookEventRepository webhookEventRepository;
    private final RazorpaySubscriptionOrderRepository subscriptionOrderRepository;
    private final RazorpayPaymentLogRepository paymentLogRepository;
    private final SubscriptionRepository coreSubscriptionRepository;
    private final ShopRepository shopRepository;
    private final PlatformTransactionManager transactionManager;

    // ═══════════════════════════════════════════════════════════════
    //  ENTRY POINT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Main webhook processing entry point called from the controller.
     *
     * @param rawPayload  raw, unmodified request body (UTF-8 string)
     * @param signature   value of the {@code X-Razorpay-Signature} header
     * @return {@code true} to respond 200 OK; {@code false} to respond 400
     */
    @Transactional
    public boolean processWebhook(String rawPayload, String signature) {
        // ── 1. Signature Verification ─────────────────────────────────────────
        boolean sigValid = RazorpaySignatureUtil.verifyWebhookSignature(
                rawPayload, signature, razorpayConfig.getWebhookSecret());

        if (!sigValid) {
            log.warn("[WEBHOOK] Invalid signature received — rejecting event");
            return false;
        }

        JSONObject payload;
        try {
            payload = new JSONObject(rawPayload);
        } catch (Exception e) {
            log.error("[WEBHOOK] Failed to parse JSON payload: {}", e.getMessage());
            return false;
        }

        String eventId   = payload.optString("id",    null);
        String eventType = payload.optString("event", null);

        if (eventId == null || eventType == null) {
            log.warn("[WEBHOOK] Missing event id or event type in payload");
            return false;
        }

        // ── 2. Idempotency Guard ──────────────────────────────────────────────
        Optional<RazorpayWebhookEvent> existing = webhookEventRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            RazorpayWebhookEvent prev = existing.get();
            prev.setAttempts(prev.getAttempts() + 1);
            webhookEventRepository.save(prev);
            if ("PROCESSED".equals(prev.getStatus())) {
                log.info("[WEBHOOK] Duplicate event {} ({}). Already PROCESSED — skipping.", eventType, eventId);
                return true;
            }
        }

        // ── 3. Persist audit record (RECEIVED) ───────────────────────────────
        RazorpayWebhookEvent eventRecord = existing.orElseGet(() -> {
            RazorpayWebhookEvent e = RazorpayWebhookEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .payload(rawPayload)
                    .status("RECEIVED")
                    .signatureVerified(true)
                    .build();
            return webhookEventRepository.save(e);
        });

        // ── 4. Dispatch ───────────────────────────────────────────────────────
        // On failure we must NOT return true — Razorpay only retries on a non-2xx
        // response, so silently ACKing a failed event means the retry never happens
        // and the subscription state permanently diverges from Razorpay's.
        try {
            dispatchEvent(eventType, payload);
            eventRecord.setStatus("PROCESSED");
            eventRecord.setProcessedAt(LocalDateTime.now());
            eventRecord.setErrorMessage(null);
            webhookEventRepository.save(eventRecord);
            return true;
        } catch (Exception e) {
            log.error("[WEBHOOK] Event {} ({}) processing failed: {}", eventType, eventId, e.getMessage(), e);
            // Roll back any partial entity writes made inside dispatchEvent — the FAILED
            // audit row itself is recorded separately in its own REQUIRES_NEW transaction
            // below so it survives this rollback.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            recordFailureInNewTransaction(eventId, eventType, rawPayload, e.getMessage());
            return false;
        }
    }

    /**
     * Persists the FAILED audit record in its own {@code REQUIRES_NEW} transaction so
     * it is committed even though the caller marks the outer transaction rollback-only.
     */
    private void recordFailureInNewTransaction(String eventId, String eventType, String rawPayload, String errorMessage) {
        TransactionTemplate tpl = new TransactionTemplate(transactionManager);
        tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tpl.executeWithoutResult(status -> {
            RazorpayWebhookEvent rec = webhookEventRepository.findByEventId(eventId).orElseGet(() -> RazorpayWebhookEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .payload(rawPayload)
                    .status("RECEIVED")
                    .signatureVerified(true)
                    .attempts(0)
                    .build());
            rec.setAttempts((rec.getAttempts() == null ? 0 : rec.getAttempts()) + 1);
            rec.setStatus("FAILED");
            rec.setErrorMessage(errorMessage);
            webhookEventRepository.save(rec);
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  DISPATCHER
    // ═══════════════════════════════════════════════════════════════

    private void dispatchEvent(String eventType, JSONObject payload) {
        JSONObject entity = payload.optJSONObject("payload");
        if (entity == null) {
            log.warn("[WEBHOOK] No payload block in event {}", eventType);
            return;
        }

        switch (eventType) {
            case "subscription.authenticated" -> handleAuthenticated(entity);
            case "subscription.activated"     -> handleActivated(entity);
            case "subscription.charged"       -> handleCharged(entity);
            case "subscription.pending"       -> handlePending(entity);
            case "subscription.halted"        -> handleHalted(entity);
            case "subscription.paused"        -> handlePaused(entity);
            case "subscription.resumed"       -> handleResumed(entity);
            case "subscription.cancelled"     -> handleCancelled(entity);
            case "subscription.completed"     -> handleCompleted(entity);
            case "subscription.expired"       -> handleExpired(entity);
            case "payment.failed"             -> handlePaymentFailed(entity);
            default -> log.info("[WEBHOOK] Unhandled event type: {} — ignoring", eventType);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  EVENT HANDLERS
    // ═══════════════════════════════════════════════════════════════

    /** Mandate authenticated — update order status only; do NOT activate subscription yet. */
    private void handleAuthenticated(JSONObject entity) {
        JSONObject subBlock = entity.optJSONObject("subscription");
        if (subBlock == null) return;
        JSONObject sub = subBlock.optJSONObject("entity");
        if (sub == null) return;

        String rzpSubId = sub.optString("id");
        subscriptionOrderRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresent(order -> {
            order.setStatus("AUTHENTICATED");
            order.setMandateStatus("ACTIVE");
            applySubscriptionTimestamps(order, sub);
            subscriptionOrderRepository.save(order);
            log.info("[WEBHOOK] subscription.authenticated for subId={}, shopId={}", rzpSubId, order.getShopId());
        });
    }

    /** Subscription activated (typically followed by a charged event). */
    private void handleActivated(JSONObject entity) {
        JSONObject subBlock = entity.optJSONObject("subscription");
        if (subBlock == null) return;
        JSONObject sub = subBlock.optJSONObject("entity");
        if (sub == null) return;

        String rzpSubId = sub.optString("id");
        subscriptionOrderRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresent(order -> {
            // A retried/duplicate delivery of this event must not extend the access
            // window a second time — only extend on the transition into ACTIVE.
            boolean alreadyActive = "ACTIVE".equalsIgnoreCase(order.getStatus());
            order.setStatus("ACTIVE");
            order.setMandateStatus("ACTIVE");
            applySubscriptionTimestamps(order, sub);
            subscriptionOrderRepository.save(order);
            if (!alreadyActive) {
                activateCoreSubscription(order);
            } else {
                log.info("[WEBHOOK] subscription.activated re-delivery for subId={} — already ACTIVE, skipping duplicate extension", rzpSubId);
            }
            log.info("[WEBHOOK] subscription.activated for subId={}, shopId={}", rzpSubId, order.getShopId());
        });
    }

    /**
     * Recurring payment successfully charged.
     * This is the most important event — it extends the subscription window.
     */
    private void handleCharged(JSONObject entity) {
        JSONObject subBlock = entity.optJSONObject("subscription");
        JSONObject payBlock = entity.optJSONObject("payment");
        if (subBlock == null) return;

        JSONObject sub = subBlock.optJSONObject("entity");
        if (sub == null) return;

        String rzpSubId = sub.optString("id");
        subscriptionOrderRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresent(order -> {
            // Update subscription order
            order.setStatus("ACTIVE");
            order.setMandateStatus("ACTIVE");
            order.setPaidCount(sub.optInt("paid_count", order.getPaidCount() != null ? order.getPaidCount() : 0));
            order.setRemainingCount(sub.optInt("remaining_count", 0));
            applySubscriptionTimestamps(order, sub);
            subscriptionOrderRepository.save(order);

            // Persist payment log (idempotent) and only extend access for a genuinely
            // new charge — a retried delivery of an already-logged payment must not
            // extend the subscription window a second time.
            boolean isNewCharge = true;
            if (payBlock != null) {
                JSONObject pay = payBlock.optJSONObject("entity");
                if (pay != null) {
                    String payId = pay.optString("id");
                    boolean alreadyLogged = paymentLogRepository.findByRazorpayPaymentId(payId).isPresent();
                    isNewCharge = !alreadyLogged;
                    if (!alreadyLogged) {
                        long amountPaise = pay.optLong("amount", 0L);
                        RazorpayPaymentLog logEntry = RazorpayPaymentLog.builder()
                                .shopId(order.getShopId())
                                .razorpaySubscriptionId(rzpSubId)
                                .razorpayPaymentId(payId)
                                .razorpayInvoiceId(pay.optString("invoice_id", null))
                                .razorpayOrderId(pay.optString("order_id", null))
                                .amount(BigDecimal.valueOf(amountPaise).divide(BigDecimal.valueOf(100)))
                                .currency(pay.optString("currency", "INR"))
                                .status("SUCCESS")
                                .invoiceStatus("PAID")
                                .method(pay.optString("method", null))
                                .bank(pay.optString("bank", null))
                                .vpa(pay.optString("vpa", null))
                                .build();
                        paymentLogRepository.save(logEntry);
                    }
                }
            }

            // Extend the core subscription access window (only for a new charge)
            if (isNewCharge) {
                activateCoreSubscription(order);
            } else {
                log.info("[WEBHOOK] subscription.charged re-delivery for subId={} — payment already logged, skipping duplicate extension", rzpSubId);
            }
            log.info("[WEBHOOK] subscription.charged for subId={}, shopId={}", rzpSubId, order.getShopId());
        });
    }

    /** Payment pending (bank delay). Mark subscription as PENDING — do not revoke access. */
    private void handlePending(JSONObject entity) {
        JSONObject subBlock = entity.optJSONObject("subscription");
        if (subBlock == null) return;
        JSONObject sub = subBlock.optJSONObject("entity");
        if (sub == null) return;

        String rzpSubId = sub.optString("id");
        subscriptionOrderRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresent(order -> {
            order.setStatus("PENDING");
            subscriptionOrderRepository.save(order);

            // Mark core subscription PENDING without revoking access
            coreSubscriptionRepository.findByShopIdUnfiltered(order.getShopId()).ifPresent(coreSub -> {
                if (coreSub.getStatus() == SubscriptionStatus.ACTIVE) {
                    coreSub.setStatus(SubscriptionStatus.PENDING);
                    coreSubscriptionRepository.save(coreSub);
                }
            });
            log.info("[WEBHOOK] subscription.pending for subId={}, shopId={}", rzpSubId, order.getShopId());
        });
    }

    /** Multiple payment failures — mandate halted. Mark as EXPIRED to revoke access. */
    private void handleHalted(JSONObject entity) {
        JSONObject subBlock = entity.optJSONObject("subscription");
        if (subBlock == null) return;
        JSONObject sub = subBlock.optJSONObject("entity");
        if (sub == null) return;

        String rzpSubId = sub.optString("id");
        subscriptionOrderRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresent(order -> {
            order.setStatus("HALTED");
            order.setMandateStatus("HALTED");
            subscriptionOrderRepository.save(order);

            // Downgrade — payment has definitively failed
            downgradeToFree(order.getShopId());
            log.warn("[WEBHOOK] subscription.halted for subId={}, shopId={} — access revoked", rzpSubId, order.getShopId());
        });
    }

    /** Paused via API call. Preserve access until current period ends. */
    private void handlePaused(JSONObject entity) {
        JSONObject subBlock = entity.optJSONObject("subscription");
        if (subBlock == null) return;
        JSONObject sub = subBlock.optJSONObject("entity");
        if (sub == null) return;

        String rzpSubId = sub.optString("id");
        subscriptionOrderRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresent(order -> {
            order.setStatus("PAUSED");
            order.setPausedAt(LocalDateTime.now());
            subscriptionOrderRepository.save(order);
            log.info("[WEBHOOK] subscription.paused for subId={}, shopId={}", rzpSubId, order.getShopId());
        });
    }

    /** Resumed via API call. */
    private void handleResumed(JSONObject entity) {
        JSONObject subBlock = entity.optJSONObject("subscription");
        if (subBlock == null) return;
        JSONObject sub = subBlock.optJSONObject("entity");
        if (sub == null) return;

        String rzpSubId = sub.optString("id");
        subscriptionOrderRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresent(order -> {
            order.setStatus("ACTIVE");
            order.setMandateStatus("ACTIVE");
            subscriptionOrderRepository.save(order);
            log.info("[WEBHOOK] subscription.resumed for subId={}, shopId={}", rzpSubId, order.getShopId());
        });
    }

    /** Subscription cancelled (immediate or end-of-cycle). Downgrade when access period ends. */
    private void handleCancelled(JSONObject entity) {
        JSONObject subBlock = entity.optJSONObject("subscription");
        if (subBlock == null) return;
        JSONObject sub = subBlock.optJSONObject("entity");
        if (sub == null) return;

        String rzpSubId = sub.optString("id");
        subscriptionOrderRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresent(order -> {
            order.setStatus("CANCELLED");
            order.setMandateStatus("CANCELLED");
            order.setCancelledAt(LocalDateTime.now());
            order.setCancelAtCycleEnd(false);
            subscriptionOrderRepository.save(order);

            // If no future endDate, downgrade immediately; otherwise leave active until endDate
            Subscription coreSub = coreSubscriptionRepository
                    .findByShopIdUnfiltered(order.getShopId()).orElse(null);
            if (coreSub == null
                    || coreSub.getEndDate() == null
                    || coreSub.getEndDate().isBefore(LocalDateTime.now())) {
                downgradeToFree(order.getShopId());
            } else {
                // Access continues until endDate; set CANCELLED so the UI shows the right state
                coreSub.setStatus(SubscriptionStatus.CANCELLED);
                coreSubscriptionRepository.save(coreSub);
            }
            log.info("[WEBHOOK] subscription.cancelled for subId={}, shopId={}", rzpSubId, order.getShopId());
        });
    }

    /** All billing cycles fulfilled. Mark completed; preserve access until period ends. */
    private void handleCompleted(JSONObject entity) {
        JSONObject subBlock = entity.optJSONObject("subscription");
        if (subBlock == null) return;
        JSONObject sub = subBlock.optJSONObject("entity");
        if (sub == null) return;

        String rzpSubId = sub.optString("id");
        subscriptionOrderRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresent(order -> {
            order.setStatus("COMPLETED");
            order.setEndedAt(LocalDateTime.now());
            subscriptionOrderRepository.save(order);
            // Access continues until endDate; let the cron job handle expiry
            log.info("[WEBHOOK] subscription.completed for subId={}, shopId={}", rzpSubId, order.getShopId());
        });
    }

    /** Subscription expired without completion. Downgrade immediately. */
    private void handleExpired(JSONObject entity) {
        JSONObject subBlock = entity.optJSONObject("subscription");
        if (subBlock == null) return;
        JSONObject sub = subBlock.optJSONObject("entity");
        if (sub == null) return;

        String rzpSubId = sub.optString("id");
        subscriptionOrderRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresent(order -> {
            order.setStatus("EXPIRED");
            order.setEndedAt(LocalDateTime.now());
            subscriptionOrderRepository.save(order);
            downgradeToFree(order.getShopId());
            log.info("[WEBHOOK] subscription.expired for subId={}, shopId={}", rzpSubId, order.getShopId());
        });
    }

    /** A payment attempt failed on a recurring charge. Log but do not revoke access yet. */
    private void handlePaymentFailed(JSONObject entity) {
        JSONObject payBlock = entity.optJSONObject("payment");
        if (payBlock == null) return;
        JSONObject pay = payBlock.optJSONObject("entity");
        if (pay == null) return;

        String rzpSubId = pay.optString("subscription_id", null);
        if (rzpSubId == null) return;

        subscriptionOrderRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresent(order -> {
            order.setFailedRetryCount(order.getFailedRetryCount() != null ? order.getFailedRetryCount() + 1 : 1);
            subscriptionOrderRepository.save(order);

            // Log the failed payment (idempotent)
            String payId = pay.optString("id", null);
            if (payId != null && paymentLogRepository.findByRazorpayPaymentId(payId).isEmpty()) {
                long amountPaise = pay.optLong("amount", 0L);
                JSONObject errBlock = pay.optJSONObject("error");
                RazorpayPaymentLog logEntry = RazorpayPaymentLog.builder()
                        .shopId(order.getShopId())
                        .razorpaySubscriptionId(rzpSubId)
                        .razorpayPaymentId(payId)
                        .amount(BigDecimal.valueOf(amountPaise).divide(BigDecimal.valueOf(100)))
                        .currency(pay.optString("currency", "INR"))
                        .status("FAILED")
                        .method(pay.optString("method", null))
                        .errorCode(errBlock != null ? errBlock.optString("code", null) : null)
                        .errorDescription(errBlock != null ? errBlock.optString("description", null) : null)
                        .build();
                paymentLogRepository.save(logEntry);
            }
            log.warn("[WEBHOOK] payment.failed for subId={}, shopId={}, attempt={}",
                    rzpSubId, order.getShopId(), order.getFailedRetryCount());
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS — Core Subscription State Machine
    // ═══════════════════════════════════════════════════════════════

    /**
     * Extends the core VyaparSathi subscription using the Razorpay order's plan
     * and billing cycle. Uses the "max of now or current endDate" rule to prevent
     * a delayed webhook from shortening an existing access window.
     *
     * <p><b>Multi-tenancy note:</b> Uses {@code findByShopIdUnfiltered} (bypasses
     * the Hibernate @Filter) because webhook processing runs without a user session
     * — the Hibernate tenant filter is never activated here.
     */
    private void activateCoreSubscription(RazorpaySubscriptionOrder order) {
        Long shopId = order.getShopId();
        Subscription coreSub = coreSubscriptionRepository
                .findByShopIdUnfiltered(shopId)
                .orElseGet(() -> {
                    Subscription s = new Subscription();
                    s.setShop(shopRepository.findById(shopId)
                            .orElseThrow(() -> new IllegalStateException("Shop not found: " + shopId)));
                    s.setUsedTrial(false);
                    return s;
                });

        Tier tier;
        try {
            tier = Tier.valueOf(order.getPlanCode().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("[WEBHOOK] Unknown planCode: {} — defaulting to PRO", order.getPlanCode());
            tier = Tier.PRO;
        }

        LocalDateTime base = (coreSub.getEndDate() != null && coreSub.getEndDate().isAfter(LocalDateTime.now()))
                ? coreSub.getEndDate()
                : LocalDateTime.now();
        LocalDateTime newEndDate = "MONTHLY".equalsIgnoreCase(order.getBillingCycle())
                ? base.plusMonths(1)
                : base.plusYears(1);

        coreSub.setTier(tier);
        coreSub.setStatus(SubscriptionStatus.ACTIVE);
        coreSub.setEndDate(newEndDate);
        if (coreSub.getStartDate() == null) coreSub.setStartDate(LocalDateTime.now());

        try {
            coreSub.setBillingCycle(BillingCycle.valueOf(order.getBillingCycle().toUpperCase()));
        } catch (IllegalArgumentException ignored) {}

        coreSubscriptionRepository.save(coreSub);
        log.info("[WEBHOOK] Core subscription extended: shopId={}, tier={}, endDate={}", shopId, tier, newEndDate);
    }

    /** Revokes premium access — sets tier to FREE and status to EXPIRED. */
    private void downgradeToFree(Long shopId) {
        coreSubscriptionRepository.findByShopIdUnfiltered(shopId).ifPresent(coreSub -> {
            coreSub.setStatus(SubscriptionStatus.EXPIRED);
            coreSub.setTier(Tier.FREE);
            coreSubscriptionRepository.save(coreSub);
            log.info("[WEBHOOK] Downgraded to FREE for shopId={}", shopId);
        });
    }

    /** Copies Razorpay subscription JSON timestamps into the local order entity. */
    private void applySubscriptionTimestamps(RazorpaySubscriptionOrder order, JSONObject sub) {
        if (sub.has("current_start") && !sub.isNull("current_start")) {
            order.setCurrentStart(epochToLdt(sub.getLong("current_start")));
        }
        if (sub.has("current_end") && !sub.isNull("current_end")) {
            order.setCurrentEnd(epochToLdt(sub.getLong("current_end")));
        }
        if (sub.has("charge_at") && !sub.isNull("charge_at")) {
            order.setChargeAt(epochToLdt(sub.getLong("charge_at")));
        }
        if (sub.has("next_cycle_at") && !sub.isNull("next_cycle_at")) {
            order.setNextChargeAt(epochToLdt(sub.getLong("next_cycle_at")));
        }
        order.setLastWebhookAt(LocalDateTime.now());
    }

    private LocalDateTime epochToLdt(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalDateTime();
    }
}
