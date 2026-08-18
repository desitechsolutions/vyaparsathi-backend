package com.desitech.vyaparsathi.customer.enums;

/**
 * Broad customer classification. Drives statutory PDF header shape
 * (B2C vs B2B), CRM segmentation, and GSTR-1 reporting bucket.
 */
public enum CustomerType {
    /** Retail buyer — no GSTIN required, no ITC eligibility. */
    INDIVIDUAL,
    /** GST-registered business — legal name + GSTIN required for tax invoices. */
    BUSINESS;

    public static CustomerType fromString(String v) {
        if (v == null || v.isBlank()) return INDIVIDUAL;
        try { return CustomerType.valueOf(v.trim().toUpperCase()); }
        catch (IllegalArgumentException ignore) { return INDIVIDUAL; }
    }
}
