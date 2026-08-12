package com.desitech.vyaparsathi.refund.enums;

/**
 * Lifecycle of a {@link com.desitech.vyaparsathi.refund.entity.Refund}.
 * Mirrored by the CHECK constraint on {@code refund.status} added in V61.
 */
public enum RefundStatus {
    /** Refund created but funds have not yet been delivered (e.g., cheque not yet handed over). */
    INITIATED,
    /** Refund fully delivered to the customer. */
    COMPLETED,
    /** Refund attempted but failed (e.g., cheque bounced). */
    FAILED
}
