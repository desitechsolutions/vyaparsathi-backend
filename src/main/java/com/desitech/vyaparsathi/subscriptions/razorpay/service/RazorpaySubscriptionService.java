package com.desitech.vyaparsathi.subscriptions.razorpay.service;

import com.desitech.vyaparsathi.common.exception.SubscriptionException;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.subscriptions.entity.Subscription;
import com.desitech.vyaparsathi.subscriptions.enums.PaymentVerificationStatus;
import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.razorpay.config.RazorpayConfig;
import com.desitech.vyaparsathi.subscriptions.razorpay.dto.CreateRazorpaySubscriptionRequest;
import com.desitech.vyaparsathi.subscriptions.razorpay.dto.RazorpayCheckoutResponse;
import com.desitech.vyaparsathi.subscriptions.razorpay.dto.RazorpaySubscriptionStatusResponse;
import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpayCustomer;
import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpayPaymentLog;
import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpaySubscriptionOrder;
import com.desitech.vyaparsathi.subscriptions.razorpay.repository.RazorpayCustomerRepository;
import com.desitech.vyaparsathi.subscriptions.razorpay.repository.RazorpayPaymentLogRepository;
import com.desitech.vyaparsathi.subscriptions.razorpay.repository.RazorpaySubscriptionOrderRepository;
import com.desitech.vyaparsathi.subscriptions.razorpay.util.RazorpaySignatureUtil;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionPayRepository;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionRepository;
import com.razorpay.Customer;
import com.razorpay.Plan;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core service for Razorpay AutoPay subscription management in VyaparSathi.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpaySubscriptionService {

    private final RazorpayConfig razorpayConfig;
    private final RazorpayClient razorpayClient;
    private final RazorpayCustomerRepository customerRepository;
    private final RazorpaySubscriptionOrderRepository subscriptionOrderRepository;
    private final RazorpayPaymentLogRepository paymentLogRepository;
    private final SubscriptionRepository coreSubscriptionRepository;
    private final SubscriptionPayRepository subscriptionPayRepository;
    private final RazorpayPricingService pricingService;
    private final ShopRepository shopRepository;

    private int getTierRank(String planCode) {
        if (planCode == null) return 0;
        return switch (planCode.toUpperCase()) {
            case "ENTERPRISE" -> 3;
            case "PRO" -> 2;
            case "STARTER" -> 1;
            default -> 0;
        };
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPER — extend subscription endDate safely
    // ═══════════════════════════════════════════════════════════════

    /**
     * Extends the subscription end date from the <em>later</em> of now or the
     * current {@code endDate}. This prevents a delayed webhook delivery from
     * shortening a customer's access window.
     */
    private LocalDateTime extendEndDate(Subscription sub, String billingCycle) {
        LocalDateTime base = (sub.getEndDate() != null && sub.getEndDate().isAfter(LocalDateTime.now()))
                ? sub.getEndDate()
                : LocalDateTime.now();
        return "MONTHLY".equalsIgnoreCase(billingCycle) ? base.plusMonths(1) : base.plusYears(1);
    }

    // ═══════════════════════════════════════════════════════════════
    //  CUSTOMER — get or create Razorpay Customer for a shop
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the existing {@link RazorpayCustomer} for the shop, or creates a
     * new one via the Razorpay API and persists it.
     */
    @Transactional
    public RazorpayCustomer getOrCreateCustomer(Long shopId, String email, String contact) {
        Optional<RazorpayCustomer> existing = customerRepository.findByShopId(shopId);
        if (existing.isPresent()) {
            log.debug("[RAZORPAY] Reusing existing customer for shopId={}", shopId);
            return existing.get();
        }

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new SubscriptionException("Shop not found: " + shopId));

        String custEmail   = (email != null && !email.isBlank())   ? email   : shop.getEmail();
        String custContact = (contact != null && !contact.isBlank()) ? contact : shop.getPhone();
        String name        = shop.getName() != null ? shop.getName() : "Shop #" + shopId;

        // Final fallbacks so Razorpay validation never rejects us
        if (custEmail   == null || custEmail.isBlank())   custEmail   = "shop" + shopId + "@vyaparsathi.app";
        if (custContact == null || custContact.isBlank()) custContact = "9999999999";

        try {
            JSONObject req = new JSONObject();
            req.put("name", name);
            req.put("email", custEmail);
            req.put("contact", custContact);
            req.put("fail_existing", 0); // Return existing customer instead of error

            JSONObject notes = new JSONObject();
            notes.put("shopId", shopId.toString());
            req.put("notes", notes);

            Customer rzpCust = razorpayClient.customers.create(req);
            String rzpCustId = rzpCust.get("id");

            RazorpayCustomer newCustomer = RazorpayCustomer.builder()
                    .shopId(shopId)
                    .razorpayCustomerId(rzpCustId)
                    .email(custEmail)
                    .contact(custContact)
                    .build();

            log.info("[RAZORPAY] Created Razorpay customer {} for shopId={}", rzpCustId, shopId);
            return customerRepository.save(newCustomer);

        } catch (RazorpayException e) {
            log.error("[RAZORPAY] Customer creation failed for shopId={}: {}", shopId, e.getMessage());
            throw new SubscriptionException("Razorpay customer creation failed: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PLAN — in-memory cache
    // ═══════════════════════════════════════════════════════════════

    private final Map<String, String> rzpPlanCache = new ConcurrentHashMap<>();

    private String getOrCreateRazorpayPlanId(String planCode, String billingCycle, BigDecimal amountInr) {
        long amountPaise = amountInr.multiply(BigDecimal.valueOf(100)).longValue();
        String cacheKey = planCode + "_" + billingCycle + "_" + amountPaise;

        if (rzpPlanCache.containsKey(cacheKey)) {
            log.info("[RAZORPAY] Reusing cached Razorpay Plan ID for key: {}", cacheKey);
            return rzpPlanCache.get(cacheKey);
        }

        try {
            JSONObject planReq = new JSONObject();
            planReq.put("period",   "MONTHLY".equalsIgnoreCase(billingCycle) ? "monthly" : "yearly");
            planReq.put("interval", 1);

            JSONObject item = new JSONObject();
            item.put("name",        "VyaparSathi " + planCode + " (" + billingCycle + ")");
            item.put("amount",      amountPaise);
            item.put("currency",    razorpayConfig.getCurrency());
            item.put("description", "VyaparSathi " + planCode + " plan (" + billingCycle + ")");
            planReq.put("item", item);

            Plan plan   = razorpayClient.plans.create(planReq);
            String planId = plan.get("id");

            rzpPlanCache.put(cacheKey, planId);
            log.info("[RAZORPAY] Created Razorpay Plan ID: {} for key: {}", planId, cacheKey);
            return planId;

        } catch (RazorpayException e) {
            log.error("[RAZORPAY] Plan creation error for key={}: {}", cacheKey, e.getMessage());
            throw new SubscriptionException("Razorpay plan creation failed: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CREATE SUBSCRIPTION ORDER
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public RazorpayCheckoutResponse createSubscriptionOrder(CreateRazorpaySubscriptionRequest request) {
        Long   shopId      = request.getShopId();
        String planCode    = request.getPlanCode().toUpperCase();
        String billingCycle = request.getBillingCycle().toUpperCase();

        // ── 0. Pending UTR Guard ─────────────────────────────────────────────
        if (subscriptionPayRepository.existsByShopIdAndStatus(shopId, PaymentVerificationStatus.WAITING)) {
            throw new SubscriptionException(
                    "A manual UTR payment is currently pending admin verification. Cancel the pending UTR request or wait for verification before subscribing via Razorpay.");
        }

        // ── Duplicate subscription & Tier Downgrade guard ────────────────────
        if (subscriptionOrderRepository.hasActiveSubscriptionForShop(shopId)) {
            Optional<RazorpaySubscriptionOrder> existingOpt =
                    subscriptionOrderRepository.findTopByShopIdOrderByCreatedAtDesc(shopId);
            if (existingOpt.isPresent()) {
                RazorpaySubscriptionOrder existing = existingOpt.get();
                String currentPlan = existing.getPlanCode() != null ? existing.getPlanCode() : "FREE";
                int currentRank = getTierRank(currentPlan);
                int targetRank = getTierRank(planCode);

                if (existing.getPlanCode().equalsIgnoreCase(planCode)
                        && existing.getBillingCycle().equalsIgnoreCase(billingCycle)) {
                    // Same plan+cycle — idempotent re-fetch
                    log.info("[RAZORPAY] Idempotent re-fetch for shopId={}, subId={}",
                            shopId, existing.getRazorpaySubscriptionId());
                    BigDecimal price = pricingService.getPrice(existing.getPlanCode(), existing.getBillingCycle());
                    return buildCheckoutResponse(existing, price);
                }

                if (targetRank < currentRank) {
                    throw new SubscriptionException(
                            "Active mandate exists. Cancel your current plan before switching to a lower tier.");
                }

                if (existing.getPlanCode().equalsIgnoreCase(planCode)) {
                    throw new SubscriptionException(
                            "An active subscription already exists for the " + planCode + " plan.");
                }
            }
        }

        // ── Price lookup ─────────────────────────────────────────────────────
        BigDecimal amountInr = pricingService.getPrice(planCode, billingCycle);

        // ── Razorpay Customer ─────────────────────────────────────────────────
        RazorpayCustomer customer = getOrCreateCustomer(
                shopId, request.getCustomerEmail(), request.getCustomerContact());

        // ── Razorpay Plan ─────────────────────────────────────────────────────
        String razorpayPlanId = getOrCreateRazorpayPlanId(planCode, billingCycle, amountInr);

        // ── Razorpay Subscription ─────────────────────────────────────────────
        try {
            JSONObject subReq = new JSONObject();
            subReq.put("plan_id",         razorpayPlanId);
            subReq.put("total_count",     "MONTHLY".equalsIgnoreCase(billingCycle) ? 12 : 1);
            subReq.put("quantity",        1);
            subReq.put("customer_notify", 1);
            subReq.put("customer_id",     customer.getRazorpayCustomerId());

            JSONObject notes = new JSONObject();
            notes.put("shopId",      shopId.toString());
            notes.put("planCode",    planCode);
            notes.put("billingCycle", billingCycle);
            subReq.put("notes", notes);

            com.razorpay.Subscription rzpSub = razorpayClient.subscriptions.create(subReq);
            String rzpSubId  = rzpSub.get("id");
            String shortUrl  = rzpSub.has("short_url") ? rzpSub.get("short_url") : null;

            RazorpaySubscriptionOrder order = RazorpaySubscriptionOrder.builder()
                    .shopId(shopId)
                    .planCode(planCode)
                    .razorpayPlanId(razorpayPlanId)
                    .razorpaySubscriptionId(rzpSubId)
                    .razorpayCustomerId(customer.getRazorpayCustomerId())
                    .status("CREATED")
                    .mandateStatus("PENDING")
                    .billingCycle(billingCycle)
                    .shortUrl(shortUrl)
                    .totalCount("MONTHLY".equalsIgnoreCase(billingCycle) ? 12 : 1)
                    .paidCount(0)
                    .remainingCount("MONTHLY".equalsIgnoreCase(billingCycle) ? 12 : 1)
                    .build();

            subscriptionOrderRepository.save(order);

            log.info("[RAZORPAY] Subscription order created: shopId={}, subId={}, plan={}, cycle={}",
                    shopId, rzpSubId, planCode, billingCycle);

            return RazorpayCheckoutResponse.builder()
                    .keyId(razorpayConfig.getKeyId())
                    .razorpaySubscriptionId(rzpSubId)
                    .razorpayCustomerId(customer.getRazorpayCustomerId())
                    .razorpayPlanId(razorpayPlanId)
                    .planCode(planCode)
                    .billingCycle(billingCycle)
                    .amount(amountInr)
                    .currency(razorpayConfig.getCurrency())
                    .status("CREATED")
                    .shortUrl(shortUrl)
                    .build();

        } catch (RazorpayException e) {
            log.error("[RAZORPAY] Subscription creation error for shopId={}: {}", shopId, e.getMessage());
            throw new SubscriptionException("Razorpay subscription creation failed: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  VERIFY & ACTIVATE CHECKOUT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Verifies the HMAC-SHA256 checkout signature returned by the Razorpay SDK
     * after the user completes mandate authentication, then activates the core
     * VyaparSathi subscription.
     *
     * <p><b>Idempotency guard:</b> If the order is already AUTHENTICATED or ACTIVE,
     * returns {@code true} immediately without re-running business logic.
     */
    @Transactional
    public boolean verifyAndActivateCheckout(Long shopId,
                                             String paymentId,
                                             String subscriptionId,
                                             String signature) {
        boolean isValid = RazorpaySignatureUtil.verifyCheckoutSignature(
                paymentId, subscriptionId, signature, razorpayConfig.getKeySecret());

        if (!isValid) {
            log.warn("[RAZORPAY] Invalid checkout signature for shopId={}, subId={}", shopId, subscriptionId);
            return false;
        }

        RazorpaySubscriptionOrder order = subscriptionOrderRepository
                .findByRazorpaySubscriptionId(subscriptionId)
                .orElseThrow(() -> new SubscriptionException(
                        "Subscription order not found: " + subscriptionId));

        // ── Idempotency guard ─────────────────────────────────────────────────
        String currentStatus = order.getStatus() != null ? order.getStatus().toUpperCase() : "";
        if ("AUTHENTICATED".equals(currentStatus) || "ACTIVE".equals(currentStatus)) {
            log.info("[RAZORPAY] Checkout already verified for subId={}. Returning true idempotently.", subscriptionId);
            return true;
        }

        order.setStatus("AUTHENTICATED");
        order.setMandateStatus("ACTIVE");
        subscriptionOrderRepository.save(order);

        // ── Payment log (idempotency on paymentId) ────────────────────────────
        if (paymentId != null && paymentLogRepository.findByRazorpayPaymentId(paymentId).isEmpty()) {
            BigDecimal price = pricingService.getPrice(order.getPlanCode(), order.getBillingCycle());
            RazorpayPaymentLog logEntry = RazorpayPaymentLog.builder()
                    .shopId(shopId)
                    .razorpaySubscriptionId(subscriptionId)
                    .razorpayPaymentId(paymentId)
                    .razorpaySignature(signature)
                    .amount(price)
                    .currency(razorpayConfig.getCurrency())
                    .status("SUCCESS")
                    .invoiceStatus("PAID")
                    .build();
            paymentLogRepository.save(logEntry);
        } else {
            log.info("[RAZORPAY] Payment log already exists for payId={}. Skipping duplicate insert.", paymentId);
        }

        // ── Activate core VyaparSathi subscription ────────────────────────────
        Subscription coreSub = coreSubscriptionRepository.findByShopId(shopId).orElseGet(() -> {
            Subscription s = new Subscription();
            s.setShop(shopRepository.findById(shopId)
                    .orElseThrow(() -> new SubscriptionException("Shop not found: " + shopId)));
            return s;
        });

        coreSub.setTier(Tier.valueOf(order.getPlanCode()));
        coreSub.setStatus(SubscriptionStatus.ACTIVE);
        coreSub.setEndDate(extendEndDate(coreSub, order.getBillingCycle()));
        coreSubscriptionRepository.save(coreSub);

        log.info("[RAZORPAY] Checkout verified & subscription activated. shopId={}, tier={}, endDate={}",
                shopId, order.getPlanCode(), coreSub.getEndDate());
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PAUSE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Pauses the active AutoPay mandate.
     * Uses pessimistic-write lock to prevent concurrent pause requests from
     * calling the Razorpay API twice for the same subscription.
     */
    @Transactional
    public void pauseSubscription(Long shopId) {
        RazorpaySubscriptionOrder order = subscriptionOrderRepository
                .findTopByShopIdOrderByCreatedAtDescForUpdate(shopId)
                .orElseThrow(() -> new SubscriptionException(
                        "No Razorpay subscription found for shop " + shopId));

        if ("PAUSED".equalsIgnoreCase(order.getStatus())) {
            throw new SubscriptionException("Subscription is already paused.");
        }
        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new SubscriptionException("Cannot pause a cancelled subscription.");
        }

        try {
            JSONObject req = new JSONObject();
            req.put("pause_at", "now");
            razorpayClient.subscriptions.pause(order.getRazorpaySubscriptionId(), req);

            order.setStatus("PAUSED");
            order.setPausedAt(LocalDateTime.now());
            subscriptionOrderRepository.save(order);
            log.info("[RAZORPAY] Subscription paused. shopId={}, subId={}", shopId, order.getRazorpaySubscriptionId());

        } catch (RazorpayException e) {
            log.error("[RAZORPAY] Pause failed for subId={}: {}", order.getRazorpaySubscriptionId(), e.getMessage());
            throw new SubscriptionException("Pause failed: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  RESUME
    // ═══════════════════════════════════════════════════════════════

    /**
     * Resumes a paused AutoPay mandate.
     * Uses pessimistic-write lock.
     */
    @Transactional
    public void resumeSubscription(Long shopId) {
        RazorpaySubscriptionOrder order = subscriptionOrderRepository
                .findTopByShopIdOrderByCreatedAtDescForUpdate(shopId)
                .orElseThrow(() -> new SubscriptionException(
                        "No Razorpay subscription found for shop " + shopId));

        if ("ACTIVE".equalsIgnoreCase(order.getStatus()) || "AUTHENTICATED".equalsIgnoreCase(order.getStatus())) {
            throw new SubscriptionException("Subscription is already active.");
        }
        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new SubscriptionException("Cannot resume a cancelled subscription. Please subscribe to a new plan.");
        }

        try {
            JSONObject req = new JSONObject();
            req.put("resume_at", "now");
            razorpayClient.subscriptions.resume(order.getRazorpaySubscriptionId(), req);

            order.setStatus("ACTIVE");
            subscriptionOrderRepository.save(order);
            log.info("[RAZORPAY] Subscription resumed. shopId={}, subId={}", shopId, order.getRazorpaySubscriptionId());

        } catch (RazorpayException e) {
            log.error("[RAZORPAY] Resume failed for subId={}: {}", order.getRazorpaySubscriptionId(), e.getMessage());
            throw new SubscriptionException("Resume failed: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CANCEL
    // ═══════════════════════════════════════════════════════════════

    /**
     * Cancels the subscription either immediately or at end-of-cycle.
     *
     * <p><b>End-of-cycle cancellation:</b> We do NOT set the status to CANCELLED
     * immediately. The Razorpay {@code subscription.cancelled} webhook event is the
     * authoritative signal. We only record {@code cancelAtCycleEnd=true} to prevent
     * the UI from allowing another cancel request and to signal to users that
     * cancellation is pending.
     */
    @Transactional
    public void cancelSubscription(Long shopId, boolean cancelAtCycleEnd) {
        RazorpaySubscriptionOrder order = subscriptionOrderRepository
                .findTopByShopIdOrderByCreatedAtDescForUpdate(shopId)
                .orElseThrow(() -> new SubscriptionException(
                        "No Razorpay subscription found for shop " + shopId));

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new SubscriptionException("Subscription is already cancelled.");
        }
        if (Boolean.TRUE.equals(order.getCancelAtCycleEnd()) && cancelAtCycleEnd) {
            throw new SubscriptionException("Subscription is already scheduled for end-of-cycle cancellation.");
        }

        try {
            JSONObject req = new JSONObject();
            req.put("cancel_at_cycle_end", cancelAtCycleEnd ? 1 : 0);
            razorpayClient.subscriptions.cancel(order.getRazorpaySubscriptionId(), req);

            if (cancelAtCycleEnd) {
                // Record intent only — webhook drives the final state transition
                order.setCancelAtCycleEnd(true);
                log.info("[RAZORPAY] End-of-cycle cancellation scheduled. shopId={}, subId={}",
                        shopId, order.getRazorpaySubscriptionId());
            } else {
                // Immediate cancellation — Razorpay confirms synchronously
                order.setStatus("CANCELLED");
                order.setMandateStatus("CANCELLED");
                order.setCancelAtCycleEnd(false);
                order.setCancelledAt(LocalDateTime.now());

                // Downgrade core subscription immediately
                coreSubscriptionRepository.findByShopId(shopId).ifPresent(sub -> {
                    sub.setStatus(SubscriptionStatus.CANCELLED);
                    sub.setTier(Tier.FREE);
                    coreSubscriptionRepository.save(sub);
                });
                log.info("[RAZORPAY] Subscription immediately cancelled. shopId={}", shopId);
            }

            subscriptionOrderRepository.save(order);

        } catch (RazorpayException e) {
            log.error("[RAZORPAY] Cancel failed for subId={}: {}", order.getRazorpaySubscriptionId(), e.getMessage());
            throw new SubscriptionException("Cancel failed: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  STATUS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns a combined subscription status aggregating the core
     * {@link Subscription} entity and the latest {@link RazorpaySubscriptionOrder}.
     */
    public RazorpaySubscriptionStatusResponse getSubscriptionStatus(Long shopId) {
        Subscription coreSub = coreSubscriptionRepository.findByShopId(shopId).orElse(null);
        Optional<RazorpaySubscriptionOrder> orderOpt =
                subscriptionOrderRepository.findTopByShopIdOrderByCreatedAtDesc(shopId);

        String   planCode  = coreSub != null ? coreSub.getTier().name() : Tier.FREE.name();
        boolean  active    = coreSub != null
                && coreSub.getStatus() == SubscriptionStatus.ACTIVE
                && coreSub.getEndDate() != null
                && coreSub.getEndDate().isAfter(LocalDateTime.now());
        LocalDateTime validTill = coreSub != null ? coreSub.getEndDate() : null;

        if (orderOpt.isEmpty()) {
            return RazorpaySubscriptionStatusResponse.builder()
                    .shopId(shopId)
                    .provider(razorpayConfig.getProvider())
                    .planCode(planCode)
                    .active(active)
                    .validTill(validTill)
                    .status("NONE")
                    .mandateStatus("NONE")
                    .billingCycle("MONTHLY")
                    .build();
        }

        RazorpaySubscriptionOrder order = orderOpt.get();
        BigDecimal price = null;
        try {
            price = pricingService.getPrice(order.getPlanCode(), order.getBillingCycle());
        } catch (Exception ignored) {}

        return RazorpaySubscriptionStatusResponse.builder()
                .shopId(shopId)
                .provider(razorpayConfig.getProvider())
                .planCode(planCode)
                .active(active)
                .validTill(validTill)
                .razorpaySubscriptionId(order.getRazorpaySubscriptionId())
                .razorpayCustomerId(order.getRazorpayCustomerId())
                .status(order.getStatus())
                .mandateStatus(order.getMandateStatus())
                .billingCycle(order.getBillingCycle())
                .nextChargeAt(order.getNextChargeAt())
                .paidCount(order.getPaidCount())
                .remainingCount(order.getRemainingCount())
                .cancelAtCycleEnd(order.getCancelAtCycleEnd())
                .priceAmount(price)
                .shortUrl(order.getShortUrl())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  PAYMENT HISTORY
    // ═══════════════════════════════════════════════════════════════

    public List<RazorpayPaymentLog> getPaymentLogs(Long shopId) {
        return paymentLogRepository.findByShopIdOrderByCreatedAtDesc(shopId);
    }

    // ═══════════════════════════════════════════════════════════════
    //  INTERNAL HELPER — build CheckoutResponse from existing order
    // ═══════════════════════════════════════════════════════════════

    private RazorpayCheckoutResponse buildCheckoutResponse(RazorpaySubscriptionOrder order, BigDecimal amount) {
        return RazorpayCheckoutResponse.builder()
                .keyId(razorpayConfig.getKeyId())
                .razorpaySubscriptionId(order.getRazorpaySubscriptionId())
                .razorpayCustomerId(order.getRazorpayCustomerId())
                .razorpayPlanId(order.getRazorpayPlanId())
                .planCode(order.getPlanCode())
                .billingCycle(order.getBillingCycle())
                .amount(amount)
                .currency(razorpayConfig.getCurrency())
                .status(order.getStatus())
                .shortUrl(order.getShortUrl())
                .build();
    }
}
