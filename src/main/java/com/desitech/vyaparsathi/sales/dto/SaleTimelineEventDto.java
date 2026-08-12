package com.desitech.vyaparsathi.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Flat, read-only projection of a single event that touched a sale — used by the
 * per-sale timeline dialog to render void / refund / cancel history without the
 * client having to hit multiple modules (audit + credit-note) itself.
 *
 * <p>Two source kinds feed into this DTO:
 * <ul>
 *   <li>{@code AuditLog} rows filtered by entity=SALE + entityId=saleId → action,
 *       username, timestamp, details. Covers CANCEL_SALE, PROCESS_RETURN,
 *       PARK_SALE, RESUME_SALE, UPDATE_SALE_NOTES, DISCARD_DRAFT.</li>
 *   <li>{@code CreditNote} rows linked to the sale → adds refNo + amount so the
 *       UI can hyperlink to the CN and show the money impact next to the audit
 *       line that recorded the return.</li>
 * </ul>
 */
public class SaleTimelineEventDto {

    /** Server-side stable key so the FE can use it as a React list key. */
    private String id;

    /** One of: CANCEL_SALE, PROCESS_RETURN, PARK_SALE, RESUME_SALE, UPDATE_SALE_NOTES, DISCARD_DRAFT, CREDIT_NOTE. */
    private String action;

    /** Human-friendly one-liner. Free-form; safe to render verbatim. */
    private String message;

    /** Username of the actor. Null for legacy rows written before username capture landed. */
    private String username;

    /** When the event was recorded. Always non-null. */
    private LocalDateTime timestamp;

    /** For CREDIT_NOTE rows — the CN number for cross-linking. Null otherwise. */
    private String refNo;

    /** For CREDIT_NOTE / PROCESS_RETURN — money moved by the event. Null when not applicable. */
    private BigDecimal amount;

    public SaleTimelineEventDto() {}

    public SaleTimelineEventDto(String id, String action, String message, String username,
                                LocalDateTime timestamp, String refNo, BigDecimal amount) {
        this.id = id;
        this.action = action;
        this.message = message;
        this.username = username;
        this.timestamp = timestamp;
        this.refNo = refNo;
        this.amount = amount;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
