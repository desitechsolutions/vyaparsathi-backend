package com.desitech.vyaparsathi.common.enums;

/**
 * CBIC-recognised supply classifications for a tax document. Drives which
 * of CGST+SGST vs IGST vs zero-rated apply, and which fields are mandatory
 * on the e-invoice/e-way bill payloads.
 *
 * <p>This enum describes the <em>nature</em> of the supply (intra-state, export, SEZ…).
 * It is distinct from the GSTR-1 <em>table routing</em> (B2B, B2CL, B2CS) which
 * depends on the customer's registration status and invoice value — that routing
 * is resolved dynamically in {@code GstTaxService}.
 *
 * <p>{@link #gstnCode()} maps each value to the corresponding GSTN API code used
 * in e-invoice, e-way bill, and GSTR-1 export/SEZ tables.
 */
public enum SupplyType {

    /** Issuer state == place-of-supply state. CGST + SGST apply. */
    INTRASTATE,

    /** Issuer state != place-of-supply state. IGST applies. */
    INTERSTATE,

    /** Supply to a SEZ unit with IGST payment. GSTN code: {@code SEZWP}. */
    SEZ_WITH_PAYMENT,

    /** Supply to a SEZ unit under LUT / Bond (zero-rated). GSTN code: {@code SEZWOP}. */
    SEZ_WITHOUT_PAYMENT,

    /** Export outside India with IGST payment. GSTN code: {@code EXPWP}. */
    EXPORT_WITH_PAYMENT,

    /** Export outside India under LUT / Bond (zero-rated). GSTN code: {@code EXPWOP}. */
    EXPORT_WITHOUT_PAYMENT,

    /** Deemed export (specific govt-notified categories). GSTN code: {@code DEXP}. */
    DEEMED_EXPORT,

    /** Composition-scheme supply — no tax charged, "Bill of Supply" issued. */
    COMPOSITION,

    /** No consideration transfer (job work, own use, samples) — challan only. */
    NON_GST;

    // ── Classification helpers ────────────────────────────────────────────────

    /** True when CGST+SGST split applies. */
    public boolean isIntraState() { return this == INTRASTATE; }

    /** True when IGST applies (includes SEZ-with-payment and deemed export). */
    public boolean isInterState() {
        return this == INTERSTATE || this == SEZ_WITH_PAYMENT
                || this == EXPORT_WITH_PAYMENT || this == DEEMED_EXPORT;
    }

    /** True when the supply is zero-rated (LUT/Bond path — no IGST charged). */
    public boolean isZeroRated() {
        return this == SEZ_WITHOUT_PAYMENT || this == EXPORT_WITHOUT_PAYMENT;
    }

    /** True when this supply appears in the GSTR-1 export table (EXP). */
    public boolean isExport() {
        return this == EXPORT_WITH_PAYMENT || this == EXPORT_WITHOUT_PAYMENT;
    }

    /** True when this supply is directed to a SEZ unit or developer. */
    public boolean isSez() {
        return this == SEZ_WITH_PAYMENT || this == SEZ_WITHOUT_PAYMENT;
    }

    // ── GSTN filing code ──────────────────────────────────────────────────────

    /**
     * Returns the GSTN API code for this supply type, as used in:
     * <ul>
     *   <li>e-invoice payload — {@code "supTyp"} field</li>
     *   <li>GSTR-1 export table — {@code "expTyp"} field</li>
     *   <li>e-way bill — {@code "supplyType"} field</li>
     * </ul>
     *
     * <p>Returns {@code null} for {@link #INTRASTATE} and {@link #INTERSTATE}
     * because those supply types are not reported as a code in export/SEZ tables —
     * they are determined from the place-of-supply vs. issuer-state comparison.
     */
    public String gstnCode() {
        return switch (this) {
            case SEZ_WITH_PAYMENT     -> "SEZWP";
            case SEZ_WITHOUT_PAYMENT  -> "SEZWOP";
            case EXPORT_WITH_PAYMENT  -> "EXPWP";
            case EXPORT_WITHOUT_PAYMENT -> "EXPWOP";
            case DEEMED_EXPORT        -> "DEXP";
            case COMPOSITION          -> "COMP";
            case NON_GST              -> "NONGST";
            // INTRASTATE and INTERSTATE are derived from place-of-supply — no GSTN code.
            default                   -> null;
        };
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    public static SupplyType fromString(String v) {
        if (v == null || v.isBlank()) return null;
        try { return SupplyType.valueOf(v.trim().toUpperCase()); }
        catch (IllegalArgumentException ignore) { return null; }
    }
}
