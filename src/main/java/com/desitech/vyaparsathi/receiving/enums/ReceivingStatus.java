package com.desitech.vyaparsathi.receiving.enums;

/**
 * GRN lifecycle:
 * <pre>
 *   DRAFT ──confirm──▶ PENDING ──receipt──▶ PARTIALLY_RECEIVED ──close──▶ COMPLETED
 *
 *   DRAFT: created via the redesigned create flow — stock NOT committed yet.
 *          Allows a receiver to build up the GRN in stages before the
 *          confirmation click that actually deducts inventory.
 *   PENDING: stock has been committed, waiting for full receipt closure.
 *   COMPLETED can also be reached from PENDING when all lines match ordered qty.
 * </pre>
 * {@code DEFAULT} predates this refactor — retained for row deserialization
 * of legacy rows; new code should treat it as PENDING.
 */
public enum ReceivingStatus {
    DRAFT,
    PENDING,
    PARTIALLY_RECEIVED,
    COMPLETED,
    CANCELLED,
    DEFAULT;

    /** True while the receiver can still edit / add lines in place. */
    public boolean isEditable() {
        return this == DRAFT;
    }

    /** True once stock has been committed and the GRN is a real record. */
    public boolean isCommitted() {
        return this == PENDING || this == PARTIALLY_RECEIVED || this == COMPLETED;
    }

    /** True when the GRN has been voided. Stock movements must be reversed. */
    public boolean isCancelled() {
        return this == CANCELLED;
    }
}