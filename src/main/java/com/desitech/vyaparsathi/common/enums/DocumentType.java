package com.desitech.vyaparsathi.common.enums;

/**
 * The universal document category enum used by the shared enterprise
 * renderer + cross-reference table. Every printable business document
 * declares itself as one of these.
 */
public enum DocumentType {
    TAX_INVOICE("TAX INVOICE"),
    BILL_OF_SUPPLY("BILL OF SUPPLY"),
    PROFORMA_INVOICE("PROFORMA INVOICE"),
    QUOTATION("QUOTATION"),
    SALES_ORDER("SALES ORDER"),
    PURCHASE_ORDER("PURCHASE ORDER"),
    DELIVERY_CHALLAN("DELIVERY CHALLAN"),
    GOODS_RECEIPT_NOTE("GOODS RECEIPT NOTE"),
    PURCHASE_RETURN("GOODS RETURN NOTE"),
    DEBIT_NOTE("DEBIT NOTE"),
    CREDIT_NOTE("CREDIT NOTE"),
    RECEIPT_VOUCHER("RECEIPT VOUCHER"),
    PAYMENT_VOUCHER("PAYMENT VOUCHER");

    private final String legalTitle;

    DocumentType(String legalTitle) { this.legalTitle = legalTitle; }

    /** The printed uppercase title required by CBIC for tax documents. */
    public String legalTitle() { return legalTitle; }

    public static DocumentType fromString(String v) {
        if (v == null || v.isBlank()) return null;
        try { return DocumentType.valueOf(v.trim().toUpperCase()); }
        catch (IllegalArgumentException ignore) { return null; }
    }
}
