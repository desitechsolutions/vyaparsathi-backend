package com.desitech.vyaparsathi.accounting.enums;

/**
 * CBIC-recognised reasons for issuing a Credit Note under §34 of the CGST Act.
 * Persisted alongside the legacy free-text {@code reason} column so historical
 * rows remain intact; drives compliance dashboard grouping + GSTR-1 export.
 */
public enum CreditNoteReasonCode {
    /** Full or partial sales return of previously-invoiced goods. */
    SALES_RETURN,
    /** Volume / early-payment / seasonal discount granted after invoice. */
    POST_SALE_DISCOUNT,
    /** Defective, damaged or otherwise non-conforming goods. */
    DEFECTIVE_GOODS,
    /** Correction of pricing / tax / party / description error on the original invoice. */
    INVOICE_CORRECTION,
    /** Anything not covered above — must be paired with a free-text reason. */
    OTHER;

    public static CreditNoteReasonCode fromString(String v) {
        if (v == null || v.isBlank()) return null;
        try { return CreditNoteReasonCode.valueOf(v.trim().toUpperCase()); }
        catch (IllegalArgumentException ignore) { return null; }
    }
}
