package com.desitech.vyaparsathi.customer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Unified transaction timeline entry that merges sales, payments,
 * credit notes, quotations, and sales orders into a single feed
 * for the customer detail page's "Overview" activity timeline.
 */
public class CustomerTransactionDto {

    /** Transaction family — e.g. SALE, PAYMENT, CREDIT_NOTE, QUOTATION, SALES_ORDER */
    private String type;

    /** Entity primary key */
    private Long id;

    /** Human-readable reference — invoice no, receipt no, CN no, quote no, SO no */
    private String refNo;

    /** Transaction date */
    private LocalDateTime date;

    /** Monetary amount */
    private BigDecimal amount = BigDecimal.ZERO;

    /** Status label — COMPLETED, PENDING, PAID, ISSUED, DRAFT, etc. */
    private String status;

    /** Short description — e.g. "Invoice INV-00012", "Payment via UPI" */
    private String description;

    // ─── Getters / Setters ─────────────────────────────────────────

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount == null ? BigDecimal.ZERO : amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
