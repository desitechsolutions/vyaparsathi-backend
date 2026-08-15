package com.desitech.vyaparsathi.document.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single party on a document — Issuer, Counterparty, Bill-To, Ship-To,
 * Consignee, Bought-From. Rendered as a labelled block in the PDF header.
 *
 * <p>Legal name and trade name are separate; trade name renders large,
 * legal name renders muted below. State + stateCode are mandatory for
 * GST determination.
 */
@Getter
@Setter
@NoArgsConstructor
public class PartyDto {

    /** ISSUER / BILL_TO / SHIP_TO / CONSIGNEE / BOUGHT_FROM */
    private String role;

    private String legalName;
    private String tradeName;
    private String pan;
    private String gstin;
    private String cin;

    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String stateCode;
    private String pincode;
    private String country;

    private String phone;
    private String email;
    private String website;

    /** Sub-block: bank details (only rendered on outgoing tax invoices). */
    private String bankName;
    private String bankAccountNumber;
    private String bankIfsc;
    private String bankBranch;
    private String upiId;
    /** Name registered against the bank account. May differ from the legal /
     *  trade name — some shops receive to a partner's or proprietor's name.
     *  Preferred as the UPI QR payee name when present. */
    private String bankHolderName;

    /** Sub-block: signatory (only on issuer). */
    private String signatoryName;
    private String signatoryDesignation;

    public String displayName() {
        if (tradeName != null && !tradeName.isBlank()) return tradeName;
        if (legalName != null && !legalName.isBlank()) return legalName;
        return "—";
    }

    public String displayLegal() {
        if (legalName != null && !legalName.isBlank()) return legalName;
        return tradeName;
    }

    public boolean isEmpty() {
        return (legalName == null || legalName.isBlank())
            && (tradeName == null || tradeName.isBlank())
            && (gstin == null || gstin.isBlank())
            && (addressLine1 == null || addressLine1.isBlank());
    }
}
