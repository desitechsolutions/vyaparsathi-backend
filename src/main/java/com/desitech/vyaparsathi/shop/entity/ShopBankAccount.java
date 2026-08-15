package com.desitech.vyaparsathi.shop.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Structured bank account for a shop. Replaces the free-text
 * {@code shop.bank_details} blob and its brittle parser — every field
 * is now a real column, so the invoice PDF and UPI QR builder read
 * unambiguous values.
 *
 * <p>A shop may have many accounts (current + escrow + payroll + EEFC);
 * only one is marked {@code is_default} per {@code currency_code}. The
 * default account is the one rendered on invoices unless a doc explicitly
 * chooses another purpose.
 */
@Entity
@Table(name = "shop_bank_account")
@Getter
@Setter
@NoArgsConstructor
public class ShopBankAccount extends ShopAwareEntity {

    /** Short human label — "Main current", "Payroll", "USD EEFC". */
    @Column(name = "label", length = 60)
    private String label;

    @Column(name = "account_holder_name", nullable = false)
    private String accountHolderName;

    @Column(name = "account_number", nullable = false, length = 40)
    private String accountNumber;

    @Column(name = "bank_name", nullable = false, length = 120)
    private String bankName;

    /** 11-char IFSC (India). Nullable for foreign-only accounts. */
    @Column(name = "ifsc_code", length = 15)
    private String ifscCode;

    @Column(name = "branch", length = 120)
    private String branch;

    /** CURRENT / SAVINGS / CC / OD / NRE / NRO / EEFC. */
    @Column(name = "account_type", length = 20)
    private String accountType;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    /** SWIFT / BIC for international wires. */
    @Column(name = "swift_code", length = 11)
    private String swiftCode;

    @Column(name = "iban", length = 34)
    private String iban;

    /** Per-account UPI — different accounts can accept UPI to different VPAs. */
    @Column(name = "upi_id", length = 80)
    private String upiId;

    /** COLLECTIONS / PAYROLL / ESCROW / GENERAL — for routing. */
    @Column(name = "purpose", length = 20)
    private String purpose;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Column(name = "display_on_invoice", nullable = false)
    private Boolean displayOnInvoice = Boolean.TRUE;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public Boolean getIsDefault() { return isDefault != null && isDefault; }
    public Boolean getIsActive() { return isActive == null || isActive; }
    public Boolean getDisplayOnInvoice() { return displayOnInvoice == null || displayOnInvoice; }
    public String getCurrencyCode() { return currencyCode != null ? currencyCode : "INR"; }
}
