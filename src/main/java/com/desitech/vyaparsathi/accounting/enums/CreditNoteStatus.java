package com.desitech.vyaparsathi.accounting.enums;

/**
 * Lifecycle of a {@link com.desitech.vyaparsathi.accounting.entity.CreditNote}.
 * Mirrored by the CHECK constraint on {@code credit_notes.status} added in V59.
 */
public enum CreditNoteStatus {
    /** Just issued — none of it has been applied yet. */
    ISSUED,
    /** Some (but not all) of the credit has been applied against invoices / refunds. */
    PARTIALLY_APPLIED,
    /** Fully consumed — applied amount equals total amount. */
    FULLY_APPLIED,
    /** Voided — no longer usable. */
    CANCELLED
}
