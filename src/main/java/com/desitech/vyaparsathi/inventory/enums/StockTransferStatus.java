package com.desitech.vyaparsathi.inventory.enums;

/**
 * Lifecycle states for a multi-location stock transfer.
 * <ul>
 *   <li>PENDING   – created but not yet executed; quantities are NOT yet moved.</li>
 *   <li>COMPLETED – both the deduction from the source location and the addition to
 *                   the destination location have been persisted as StockMovements.</li>
 *   <li>CANCELLED – the transfer was voided before execution; no stock was moved.</li>
 * </ul>
 */
public enum StockTransferStatus {
    PENDING,
    /** V94 — awaiting manager approval before the transfer can move. */
    PENDING_APPROVAL,
    /** V94 — approved and dispatched but not yet received at the destination. */
    IN_TRANSIT,
    COMPLETED,
    CANCELLED
}
