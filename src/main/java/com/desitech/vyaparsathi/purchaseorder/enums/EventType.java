package com.desitech.vyaparsathi.purchaseorder.enums;

public enum EventType {
    CREATED,
    SUBMITTED,
    UPDATED,
    CANCELLED,
    RECEIVED,
    PLACED,
    DELETED,
    // V85 (Phase 3) approval workflow — downstream listeners (receiving,
    // analytics) ignore unknown types today, so adding these is safe.
    APPROVAL_REQUESTED,
    APPROVED,
    REJECTED,
    /** Distinct from UPDATED: the requester moved a REJECTED PO back into
     * DRAFT via the /revise endpoint. Audit / notification subscribers use
     * this to tell "admin edited a line item" from "requester revised after
     * rejection." */
    REVISED
}