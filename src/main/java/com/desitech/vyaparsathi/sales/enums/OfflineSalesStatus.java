package com.desitech.vyaparsathi.sales.enums;

/**
 * Offline Sales Queue Status
 *
 * Status Transitions:
 *   DRAFT ────────────┐
 *   ↓                 │
 *   PENDING ──────────┤
 *   ↓                 │
 *   PROCESSING        │
 *   ├─ COMPLETED ─────┤
 *   ├─ FAILED ────────┤ (retryable - goes back to PENDING)
 *   └─ CONFLICT ──────┘ (manual review needed)
 */
public enum OfflineSalesStatus {
    /**
     * Just received from frontend, not yet processed
     */
    DRAFT,

    /**
     * Queued for processing, waiting for background job
     */
    PENDING,

    /**
     * Currently being processed by background job
     */
    PROCESSING,

    /**
     * Successfully created sale + generated invoice
     */
    COMPLETED,

    /**
     * Network/validation error occurred
     * Will be retried automatically (up to max retries)
     */
    FAILED,

    /**
     * Conflict detected (e.g., 409, duplicate, data mismatch)
     * Requires manual review and intervention
     */
    CONFLICT
}
