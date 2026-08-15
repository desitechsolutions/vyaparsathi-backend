package com.desitech.vyaparsathi.shop.entity;

import com.desitech.vyaparsathi.common.util.LocalDateTimeAttributeConverter;
import com.desitech.vyaparsathi.shop.enums.IndustryType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop")
@Getter
@Setter
@NoArgsConstructor
public class Shop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String ownerName;

    private String address;

    private String phone;
    private String email;

    @Column(nullable = false)
    private String state;

    @Column(name = "state_code", length = 2)
    private String stateCode;

    private String gstin;

    @Column(unique = true, nullable = false)
    private String code;

    private String locale;

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();


    @Column(nullable = true)
    private String logoPath;
    private String signaturePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "industry_type", length = 30)
    private IndustryType industryType;

    @Column(name = "is_composition_scheme")
    private Boolean isCompositionScheme = false;

    @Column(name = "brand_color")
    private String brandColor = "#2980b9";

    @Column(nullable = false)
    private Boolean active = true;

    @Column(columnDefinition = "TEXT")
    private String termsAndConditions;

    @Column(name = "bank_details", columnDefinition = "TEXT")
    private String bankDetails;

    @Column(name = "upi_id")
    private String upiId;

    @Column(name = "invoice_prefix")
    private String invoicePrefix;

    @Column(name = "company_website")
    private String companyWebsite;

    @Column(name = "invoice_footer", columnDefinition = "TEXT")
    private String invoiceFooter;

    @Column(name = "support_contact")
    private String supportContact;

    @Column(name = "invoice_due_days")
    private Integer invoiceDueDays = 30;

    /**
     * Opt-in flag for the daily low-stock-alerts email digest (V80).
     * The scheduler skips a shop whose flag is false or whose {@code email}
     * is null — no accidental broadcast to shops that never asked for it.
     */
    @Column(name = "low_stock_alerts_enabled", nullable = false)
    private Boolean lowStockAlertsEnabled = Boolean.FALSE;

    /**
     * Opt-in flag for SMS alerts. Provider integration is a follow-up —
     * the flag ships now so the Shop Settings UI carries both toggles
     * from day one and no schema change is needed once SMS dispatch is
     * wired.
     */
    @Column(name = "low_stock_sms_alerts_enabled", nullable = false)
    private Boolean lowStockSmsAlertsEnabled = Boolean.FALSE;

    // ─── V85 (Phase 3) PO approval policy ──────────────────────────────
    // Opt-in per shop: when poApprovalRequired is true, any PO submit whose
    // grand total meets or exceeds poApprovalThresholdAmount is routed into
    // PENDING_APPROVAL instead of straight-to-SUBMITTED. Threshold of 0 means
    // "every PO needs approval regardless of amount". Default is disabled so
    // existing shops keep the previous DRAFT → SUBMITTED flow.
    @Column(name = "po_approval_required", nullable = false)
    private Boolean poApprovalRequired = Boolean.FALSE;

    @Column(name = "po_approval_threshold_amount", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal poApprovalThresholdAmount = java.math.BigDecimal.ZERO;

    /**
     * V94 — adjustments whose absolute value (delta × WAC) exceeds this
     * threshold are held for OWNER approval before commit. Null / 0 disables
     * the gate.
     */
    @Column(name = "adjustment_approval_threshold", precision = 14, scale = 2)
    private java.math.BigDecimal adjustmentApprovalThreshold;

    // ─── V99 — statutory issuer identity ──────────────────────────────
    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "pan", length = 10)
    private String pan;

    @Column(name = "cin", length = 21)
    private String cin;

    @Column(name = "signatory_name", length = 120)
    private String signatoryName;

    @Column(name = "signatory_designation", length = 80)
    private String signatoryDesignation;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "country", length = 80)
    private String country = "IN";

    @Column(name = "e_invoicing_enabled", nullable = false)
    private Boolean eInvoicingEnabled = Boolean.FALSE;

    @Column(name = "e_way_bill_enabled", nullable = false)
    private Boolean eWayBillEnabled = Boolean.FALSE;

    @Column(name = "digital_signing_enabled", nullable = false)
    private Boolean digitalSigningEnabled = Boolean.FALSE;

    public String getLegalName() { return legalName != null ? legalName : name; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public String getTradeName() { return tradeName != null ? tradeName : name; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }

    public String getPan() { return pan; }
    public void setPan(String pan) { this.pan = pan; }

    public String getCin() { return cin; }
    public void setCin(String cin) { this.cin = cin; }

    public String getSignatoryName() { return signatoryName; }
    public void setSignatoryName(String signatoryName) { this.signatoryName = signatoryName; }

    public String getSignatoryDesignation() { return signatoryDesignation; }
    public void setSignatoryDesignation(String signatoryDesignation) { this.signatoryDesignation = signatoryDesignation; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public String getCountry() { return country != null ? country : "IN"; }
    public void setCountry(String country) { this.country = country; }

    public Boolean getEInvoicingEnabled() { return eInvoicingEnabled != null ? eInvoicingEnabled : Boolean.FALSE; }
    public void setEInvoicingEnabled(Boolean v) { this.eInvoicingEnabled = v; }

    public Boolean getEWayBillEnabled() { return eWayBillEnabled != null ? eWayBillEnabled : Boolean.FALSE; }
    public void setEWayBillEnabled(Boolean v) { this.eWayBillEnabled = v; }

    public Boolean getDigitalSigningEnabled() { return digitalSigningEnabled != null ? digitalSigningEnabled : Boolean.FALSE; }
    public void setDigitalSigningEnabled(Boolean v) { this.digitalSigningEnabled = v; }

    public java.math.BigDecimal getAdjustmentApprovalThreshold() { return adjustmentApprovalThreshold; }
    public void setAdjustmentApprovalThreshold(java.math.BigDecimal adjustmentApprovalThreshold) {
        this.adjustmentApprovalThreshold = adjustmentApprovalThreshold;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }

    public String getSignaturePath() { return signaturePath; }
    public void setSignaturePath(String signaturePath) { this.signaturePath = signaturePath; }

    public IndustryType getIndustryType() { return industryType; }
    public void setIndustryType(IndustryType industryType) { this.industryType = industryType; }

    public Boolean getIsCompositionScheme() { return isCompositionScheme; }
    public void setIsCompositionScheme(Boolean isCompositionScheme) { this.isCompositionScheme = isCompositionScheme; }

    public String getBrandColor() { return brandColor; }
    public void setBrandColor(String brandColor) { this.brandColor = brandColor; }

    public String getTermsAndConditions() { return termsAndConditions; }
    public void setTermsAndConditions(String termsAndConditions) { this.termsAndConditions = termsAndConditions; }

    public String getBankDetails() { return bankDetails; }
    public void setBankDetails(String bankDetails) { this.bankDetails = bankDetails; }

    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }

    public String getInvoicePrefix() { return invoicePrefix; }
    public void setInvoicePrefix(String invoicePrefix) { this.invoicePrefix = invoicePrefix; }

    public String getCompanyWebsite() { return companyWebsite; }
    public void setCompanyWebsite(String companyWebsite) { this.companyWebsite = companyWebsite; }

    public String getInvoiceFooter() { return invoiceFooter; }
    public void setInvoiceFooter(String invoiceFooter) { this.invoiceFooter = invoiceFooter; }

    public String getSupportContact() { return supportContact; }
    public void setSupportContact(String supportContact) { this.supportContact = supportContact; }

    public Integer getInvoiceDueDays() { return invoiceDueDays; }
    public void setInvoiceDueDays(Integer invoiceDueDays) { this.invoiceDueDays = invoiceDueDays; }

    public Boolean getActive() { return active != null ? active : true; }
    public void setActive(Boolean active) { this.active = active; }

    public Boolean getLowStockAlertsEnabled() {
        return lowStockAlertsEnabled != null ? lowStockAlertsEnabled : Boolean.FALSE;
    }
    public void setLowStockAlertsEnabled(Boolean lowStockAlertsEnabled) {
        this.lowStockAlertsEnabled = lowStockAlertsEnabled;
    }

    public Boolean getLowStockSmsAlertsEnabled() {
        return lowStockSmsAlertsEnabled != null ? lowStockSmsAlertsEnabled : Boolean.FALSE;
    }
    public void setLowStockSmsAlertsEnabled(Boolean lowStockSmsAlertsEnabled) {
        this.lowStockSmsAlertsEnabled = lowStockSmsAlertsEnabled;
    }

    public Boolean getPoApprovalRequired() {
        return poApprovalRequired != null ? poApprovalRequired : Boolean.FALSE;
    }
    public void setPoApprovalRequired(Boolean poApprovalRequired) {
        this.poApprovalRequired = poApprovalRequired;
    }

    public java.math.BigDecimal getPoApprovalThresholdAmount() {
        return poApprovalThresholdAmount != null ? poApprovalThresholdAmount : java.math.BigDecimal.ZERO;
    }
    public void setPoApprovalThresholdAmount(java.math.BigDecimal poApprovalThresholdAmount) {
        this.poApprovalThresholdAmount = poApprovalThresholdAmount;
    }
}