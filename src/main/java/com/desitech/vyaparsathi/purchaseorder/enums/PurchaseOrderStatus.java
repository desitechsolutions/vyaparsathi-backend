package com.desitech.vyaparsathi.purchaseorder.enums;

/**
 * Purchase-order lifecycle states.
 *
 * <h2>Canonical transitions (Phase 1 of the enterprise redesign)</h2>
 * <pre>
 *   DRAFT ──submit──▶ SUBMITTED
 *     │                 │
 *     │                 └──receiving started──▶ PARTIALLY_RECEIVED
 *     │                                          │
 *     │                                          └──all lines received──▶ RECEIVED
 *     │
 *     └──cancel──▶ CANCELLED  (also reachable from SUBMITTED / PARTIALLY_RECEIVED)
 *
 *   RECEIVED and CANCELLED are terminal.
 *   Only DRAFT can be hard-deleted; all other statuses require cancellation.
 * </pre>
 *
 * <h2>Deprecated values</h2>
 * <ul>
 *   <li>{@link #PENDING} — legacy; no service method writes it any more.
 *       Kept in the enum only so existing rows still deserialize; V81
 *       maps every existing PENDING row to SUBMITTED. Do not use in new
 *       code.</li>
 *   <li>{@link #IN_PROGRESS} — same story; V81 remaps to PARTIALLY_RECEIVED.</li>
 * </ul>
 * Removing them entirely would need a schema-level enum shrink + a purge
 * of any lingering rows, which is out of scope for Phase 1.
 */
public enum PurchaseOrderStatus {
    DRAFT,
    /** V85 (Phase 3) — shop policy requires approval before the PO can be
     * committed to the supplier. Reached from DRAFT via submit-with-policy.
     * From here: approve → SUBMITTED, reject → REJECTED. */
    PENDING_APPROVAL,
    /**
     * V85 refactor — the approver rejected the PO. Read-only in this state so
     * the approver's comment sticks with the document; the requester clicks
     * "Revise PO" which transitions REJECTED → DRAFT (keeping rejection_reason
     * as a reference banner) so they can edit and resubmit for approval.
     * Not terminal, not editable directly.
     */
    REJECTED,
    SUBMITTED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED,

    /** @deprecated Use SUBMITTED. Retained for backward-compat only. */
    @Deprecated
    PENDING,

    /** @deprecated Use PARTIALLY_RECEIVED. Retained for backward-compat only. */
    @Deprecated
    IN_PROGRESS;

    /**
     * True when the PO can still be modified in-place.
     * DRAFT accepts edits; PENDING_APPROVAL is locked (edits would let the
     * requester silently increase the amount past the approver's inspection).
     */
    public boolean isEditable() {
        return this == DRAFT;
    }

    /** True when the PO has reached a permanent terminal state. */
    public boolean isTerminal() {
        return this == RECEIVED || this == CANCELLED;
    }

    /** True when receiving activity is still expected against this PO. */
    public boolean isOpenForReceipt() {
        return this == SUBMITTED || this == PARTIALLY_RECEIVED;
    }

    /** True when the PO is waiting for an approver. */
    public boolean isPendingApproval() {
        return this == PENDING_APPROVAL;
    }
}
