package com.desitech.vyaparsathi.sales.enums;

/**
 * Lifecycle states of a sale.
 *
 * <ul>
 *   <li>{@link #DRAFT} — cart in progress; not billed, no ledger, no stock impact.</li>
 *   <li>{@link #COMPLETED} — finalized invoice/proforma; stock deducted, ledger posted.</li>
 *   <li>{@link #PARTIALLY_RETURNED} — some (not all) line quantities have been returned;
 *       the sale is still legally on record, but the total has been adjusted.</li>
 *   <li>{@link #RETURNED} — every line quantity has been fully returned.</li>
 *   <li>{@link #CANCELLED} — sale voided, stock restored, payments moved to advance.</li>
 * </ul>
 */
public enum SaleStatus {
    DRAFT,
    /**
     * Explicitly parked sale — same shape as a DRAFT (no ledger, no stock impact)
     * but marks user intent: "I paused this order to serve the next customer,
     * come back to it later." Distinguishing lets the UI surface a "Resume" action
     * separately from auto-saved drafts. Convertible: HELD → DRAFT (resume) or
     * HELD → COMPLETED (once resumed and finalized).
     */
    HELD,
    COMPLETED,
    PARTIALLY_RETURNED,
    RETURNED,
    CANCELLED
}
