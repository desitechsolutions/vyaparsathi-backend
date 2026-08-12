package com.desitech.vyaparsathi.quotation.enums;

/**
 * Lifecycle of a {@link com.desitech.vyaparsathi.quotation.entity.Quotation}.
 * Mirrored by the CHECK constraint on {@code quotation.status} in V63.
 *
 * <p>Allowed transitions:
 * <pre>
 *   DRAFT      → SENT | CANCELLED
 *   SENT       → ACCEPTED | REJECTED | EXPIRED | CANCELLED
 *   ACCEPTED   → CONVERTED | CANCELLED
 *   REJECTED   → (terminal)
 *   EXPIRED    → (terminal — but user can re-quote)
 *   CANCELLED  → (terminal)
 *   CONVERTED  → (terminal — a Sale exists for this quote)
 * </pre>
 */
public enum QuotationStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    CANCELLED,
    CONVERTED
}
