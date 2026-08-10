package com.desitech.vyaparsathi.subscriptions.razorpay.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable payment event record written once per successful or failed Razorpay
 * payment (both checkout-time and recurring auto-charge).
 *
 * <p>The {@code razorpayPaymentId} column has a unique constraint — the service
 * layer checks for its existence before inserting to ensure idempotent processing
 * of duplicate webhook deliveries.
 */
@Entity
@Table(name = "razorpay_payment_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayPaymentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "razorpay_subscription_id", nullable = false, length = 100)
    private String razorpaySubscriptionId;

    /** Unique Razorpay payment ID (e.g. {@code pay_XXXX}). Used as idempotency key. */
    @Column(name = "razorpay_payment_id", nullable = false, unique = true, length = 100)
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature")
    private String razorpaySignature;

    @Column(name = "razorpay_invoice_id", length = 100)
    private String razorpayInvoiceId;

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    /** Amount in INR (already converted from paise on write). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    /** Payment outcome. Values: SUCCESS | FAILED | REFUNDED */
    @Column(nullable = false, length = 50)
    private String status;

    /** Invoice payment state. Values: PAID | UNPAID */
    @Column(name = "invoice_status", length = 50)
    private String invoiceStatus;

    /** Payment instrument. Values: card | upi | netbanking | emandate */
    @Column(length = 50)
    private String method;

    @Column(name = "card_id", length = 100)
    private String cardId;

    @Column(length = 50)
    private String bank;

    /** VPA (UPI address) used for payment. */
    @Column(length = 100)
    private String vpa;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_description", columnDefinition = "TEXT")
    private String errorDescription;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (currency == null) currency = "INR";
    }
}
