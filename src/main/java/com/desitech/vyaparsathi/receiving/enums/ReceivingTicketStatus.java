package com.desitech.vyaparsathi.receiving.enums;

/**
 * Lifecycle for a receiving dispute:
 * <pre>
 *   OPEN ──picked up──▶ IN_PROGRESS ──resolve──▶ RESOLVED ──confirm──▶ CLOSED
 *   OPEN can also skip straight to CLOSED (invalid ticket).
 * </pre>
 * Persisted as VARCHAR via @Enumerated(STRING) so migrations don't need
 * schema changes for future enum additions. V88 backfilled legacy string
 * rows to OPEN.
 */
public enum ReceivingTicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    /** True while someone still needs to act on this ticket. */
    public boolean isOpen() {
        return this == OPEN || this == IN_PROGRESS;
    }

    /** True when the ticket has left the queue for good. */
    public boolean isTerminal() {
        return this == RESOLVED || this == CLOSED;
    }

    /**
     * Lenient parser — accepts legacy free-text values so pre-V88 rows still
     * deserialize when read back via findAll(). Unknown values → OPEN.
     */
    public static ReceivingTicketStatus fromString(String value) {
        if (value == null || value.isBlank()) return OPEN;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }
}