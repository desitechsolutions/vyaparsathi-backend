package com.desitech.vyaparsathi.subscriptions.razorpay.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Audit table for every Razorpay webhook delivery.
 *
 * <p>The {@code eventId} column (unique) is the primary idempotency key.
 * Before processing any event, the webhook service checks whether a record
 * with that {@code eventId} already exists and has {@code status = PROCESSED}.
 * If so, the event is skipped immediately and the service returns {@code true}
 * (telling Razorpay "we got it") without re-applying business logic.
 *
 * <p>On failure the record is saved with {@code status = FAILED} and an error
 * message so that operations teams can triage from the database directly.
 */
@Entity
@Table(name = "razorpay_webhook_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Razorpay-assigned event ID. Used as the idempotency key. */
    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    /** e.g. {@code subscription.charged}, {@code subscription.cancelled}. */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** Full raw JSON payload as received. Stored for audit / replay. */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    /** Values: RECEIVED | PROCESSED | FAILED | IGNORED */
    @Column(nullable = false, length = 30)
    private String status;

    /** Incremented on every re-delivery attempt for the same eventId. */
    @Column(name = "attempts")
    private Integer attempts;

    @Column(name = "signature_verified")
    private Boolean signatureVerified;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "RECEIVED";
        if (attempts == null) attempts = 1;
        if (signatureVerified == null) signatureVerified = false;
        if (httpStatus == null) httpStatus = 200;
    }
}
