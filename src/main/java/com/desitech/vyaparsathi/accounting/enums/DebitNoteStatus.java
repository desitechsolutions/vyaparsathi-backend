package com.desitech.vyaparsathi.accounting.enums;

/**
 * Lifecycle of a {@link com.desitech.vyaparsathi.accounting.entity.DebitNote}.
 * Mirrored by the CHECK constraint on {@code debit_notes.status} added in V60.
 */
public enum DebitNoteStatus {
    ISSUED,
    PARTIALLY_APPLIED,
    FULLY_APPLIED,
    CANCELLED
}
