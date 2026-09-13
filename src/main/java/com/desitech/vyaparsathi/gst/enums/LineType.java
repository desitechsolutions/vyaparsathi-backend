package com.desitech.vyaparsathi.gst.enums;

/**
 * Classifies a sale/purchase line as a physical good or a service.
 *
 * <p>This distinction is mandatory for HSN Summary (GSTR-1 Table 12):
 * <ul>
 *   <li>Goods lines carry an <b>HSN</b> code (Harmonised System of Nomenclature)
 *       and a <b>UQC</b> (Unit Quantity Code) such as NOS, KGS, MTR.</li>
 *   <li>Service lines carry a <b>SAC</b> code (Services Accounting Code, prefix 99)
 *       and the UQC is always reported as {@code "NA"} to GSTN.</li>
 * </ul>
 *
 * <p>Set on {@code ItemVariant} at catalog time; propagated to {@code SaleItem}
 * and {@code PurchaseInvoiceItem} at transaction time.
 */
public enum LineType {

    /**
     * Physical goods — HSN code + UQC required.
     * Default for all existing inventory-backed catalog items.
     */
    GOODS,

    /**
     * Services — SAC code required; UQC must be {@code "NA"} in GSTN filings.
     * Applies to: labour, freight, consultation, job-work charges, and any
     * custom line item where no physical goods are transferred.
     */
    SERVICES;

    /** Convenience: true when HSN + UQC is required for GSTN reporting. */
    public boolean requiresUqc() {
        return this == GOODS;
    }
}
