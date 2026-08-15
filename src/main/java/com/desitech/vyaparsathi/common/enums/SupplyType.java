package com.desitech.vyaparsathi.common.enums;

/**
 * CBIC-recognised supply classifications for a tax document. Drives which
 * of CGST+SGST vs IGST vs zero-rated apply, and which fields are mandatory
 * on the e-invoice/e-way bill payloads.
 */
public enum SupplyType {

    /** Issuer state == place-of-supply state. CGST + SGST apply. */
    INTRASTATE,

    /** Issuer state != place-of-supply state. IGST applies. */
    INTERSTATE,

    /** Supply to a SEZ unit with IGST payment. */
    SEZ_WITH_PAYMENT,

    /** Supply to a SEZ unit under LUT / Bond (zero-rated). */
    SEZ_WITHOUT_PAYMENT,

    /** Export outside India with IGST payment. */
    EXPORT_WITH_PAYMENT,

    /** Export outside India under LUT / Bond (zero-rated). */
    EXPORT_WITHOUT_PAYMENT,

    /** Deemed export (specific govt-notified categories). */
    DEEMED_EXPORT,

    /** Composition-scheme supply — no tax charged, "Bill of Supply" issued. */
    COMPOSITION,

    /** No consideration transfer (job work, own use, samples) — challan only. */
    NON_GST;

    /** Convenience: true when CGST+SGST split applies. */
    public boolean isIntraState() { return this == INTRASTATE; }

    /** Convenience: true when IGST applies. */
    public boolean isInterState() {
        return this == INTERSTATE || this == SEZ_WITH_PAYMENT
                || this == EXPORT_WITH_PAYMENT || this == DEEMED_EXPORT;
    }

    /** Convenience: true when the supply is zero-rated. */
    public boolean isZeroRated() {
        return this == SEZ_WITHOUT_PAYMENT || this == EXPORT_WITHOUT_PAYMENT;
    }

    public static SupplyType fromString(String v) {
        if (v == null || v.isBlank()) return null;
        try { return SupplyType.valueOf(v.trim().toUpperCase()); }
        catch (IllegalArgumentException ignore) { return null; }
    }
}
