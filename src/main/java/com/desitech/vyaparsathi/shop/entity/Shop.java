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
}