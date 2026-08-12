package com.desitech.vyaparsathi.sales.enums;

/**
 * What kind of Sale this row represents.
 *
 * <ul>
 *   <li>{@link #INVOICE} — a real, GST-compliant tax invoice. Deducts stock,
 *       posts to the customer ledger, uses the shop's invoice-prefix number
 *       series. This is the default.</li>
 *   <li>{@link #PROFORMA} — a non-binding pre-invoice sent for customer
 *       approval or as import/export documentation. Does NOT deduct stock,
 *       does NOT post to the customer ledger. Uses the {@code PI/YY-YY/NNNNN}
 *       number series. Convertible to a real {@link #INVOICE} via
 *       {@code SaleService.convertProformaToInvoice(...)}.</li>
 * </ul>
 */
public enum SaleType {
    INVOICE,
    PROFORMA,
    /**
     * Bill of Supply — used by dealers under the GST composition scheme, or when
     * dealing exclusively in exempt goods. NO GST is charged (CGST/SGST/UTGST/IGST
     * all zero on every line, regardless of variant.gstRate). Uses a distinct
     * {@code BOS/YY-YY/NNNNN} number series to keep the tax-invoice sequence pure.
     */
    BILL_OF_SUPPLY
}
