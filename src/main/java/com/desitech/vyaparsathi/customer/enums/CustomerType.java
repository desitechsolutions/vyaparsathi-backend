package com.desitech.vyaparsathi.customer.enums;

/**
 * Broad customer classification. Drives statutory PDF header shape,
 * CRM segmentation, and — critically — GSTR-1 reporting bucket:
 * B2B vs B2C-large vs B2C-small vs GOV (deemed export) vs EXP
 * (zero-rated with/without payment of tax under LUT / bond).
 */
public enum CustomerType {
    /** Retail buyer — no GSTIN required, no ITC eligibility. */
    INDIVIDUAL,
    /** GST-registered business — legal name + GSTIN required for tax invoices. */
    BUSINESS,
    /**
     * Government / PSU customer. GST invoices carry TDS deduction
     * (Section 51) and route to GSTR-7 filing on the deductor's side.
     * Treated as "deemed B2B" for GSTR-1 Table 4 but flagged for
     * downstream TDS handling.
     */
    GOVERNMENT,
    /**
     * Export customer. Two GSTR-1 sub-cases:
     * <ul>
     *   <li>With payment of IGST (Table 6A "WPAY").</li>
     *   <li>Under LUT / bond, zero-rated (Table 6A "WOPAY").</li>
     * </ul>
     * Distinguished by shipping address country != India.
     */
    EXPORT;

    public static CustomerType fromString(String v) {
        if (v == null || v.isBlank()) return INDIVIDUAL;
        try { return CustomerType.valueOf(v.trim().toUpperCase()); }
        catch (IllegalArgumentException ignore) { return INDIVIDUAL; }
    }
}
