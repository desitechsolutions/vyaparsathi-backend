package com.desitech.vyaparsathi.gst.enums;

/**
 * Classifies a credit note for GSTR-1 routing.
 *
 * <ul>
 *   <li>{@link #CDNR} — Credit/Debit Note to a <em>Registered</em> recipient.
 *       Filed in <b>GSTR-1 Table 9</b>. The recipient's GSTIN must be present.</li>
 *   <li>{@link #CDNUR} — Credit/Debit Note to an <em>Unregistered</em> recipient.
 *       Filed in <b>GSTR-1 Table 10</b>. No GSTIN on the customer record.</li>
 * </ul>
 *
 * <p>Set at credit-note creation time based on whether the linked customer
 * has a non-blank {@code gstNumber}. Once set, this value must <em>not</em>
 * be changed — GSTN ties the note to a specific filing table.
 */
public enum NoteType {

    /**
     * Credit/Debit Note to a GST-registered recipient.
     * GSTR-1 schema field {@code "ntty": "C"} (credit note) or {@code "ntty": "D"} (debit note).
     * Maps to GSTN Table 9 — CDNR.
     */
    CDNR,

    /**
     * Credit/Debit Note to a GST-unregistered (B2C) recipient.
     * GSTR-1 schema field {@code "ntty": "C"} / {@code "ntty": "D"} inside Table 10 — CDNUR.
     * Applicable when the original supply would have been classified B2CS or B2CL.
     */
    CDNUR;

    /** Returns the GSTN JSON value for the {@code ntty} field of a credit note. */
    public static NoteType fromCustomerGstin(String gstin) {
        return (gstin != null && !gstin.isBlank()) ? CDNR : CDNUR;
    }
}
