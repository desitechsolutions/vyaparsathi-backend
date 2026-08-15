package com.desitech.vyaparsathi.purchaseorder.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body for {@code POST /api/purchase-orders/{id}/send} (V86 / Phase 5).
 * Every field is optional — an empty body still works (server falls back
 * to supplier.email, generates the subject/body from the PO). Explicit
 * overrides win when supplied.
 */
@Data
public class PurchaseOrderSendDto {

    /** Recipient override — defaults to supplier.email when omitted. */
    @Email(message = "Enter a valid email address")
    @Size(max = 255)
    private String to;

    /** Subject override — server generates a sensible default when omitted. */
    @Size(max = 255)
    private String subject;

    /** Body override — plain text or HTML (server wraps plain text). */
    @Size(max = 5000)
    private String body;

    /** When true (default), the PDF is attached to the outgoing email. */
    private Boolean attachPdf = Boolean.TRUE;
}