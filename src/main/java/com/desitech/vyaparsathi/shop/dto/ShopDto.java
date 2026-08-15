package com.desitech.vyaparsathi.shop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShopDto {

    private Long id;

    @NotBlank(message = "Shop name is required")
    private String name;

    private String ownerName;
    private String address;

    @NotBlank(message = "State is required")
    private String state;

    /** 2-digit GSTN state code (e.g. "27" for Maharashtra). Optional on write during transition; required for accurate GST split downstream. */
    private String stateCode;

    private String gstin;

    @NotBlank(message = "Shop code is required")
    private String code;

    private String locale;

    @NotBlank(message = "Industry type is required")
    private String industryType;

    private String phone;
    private String email;

    private String logoPath;
    private String signaturePath;

    private Boolean isCompositionScheme;
    private String brandColor;

    private String termsAndConditions;
    private String bankDetails;

    private String upiId;
    private String invoicePrefix;
    private String companyWebsite;
    private String invoiceFooter;
    private String supportContact;
    private Integer invoiceDueDays;
    private String accessToken;
    private String refreshToken;

    /** Notification preferences (V80). Both default to false server-side. */
    private Boolean lowStockAlertsEnabled;
    private Boolean lowStockSmsAlertsEnabled;
    // V85 Phase 3: purchase-order approval policy.
    private Boolean poApprovalRequired;
    private java.math.BigDecimal poApprovalThresholdAmount;

    // ─── V99 — enterprise identity fields ───────────────────────────────
    private String legalName;
    private String tradeName;
    private String pan;
    private String cin;
    private String signatoryName;
    private String signatoryDesignation;
    private String addressLine2;
    private String city;
    private String pincode;
    private String country;
    private Boolean eInvoicingEnabled;
    private Boolean eWayBillEnabled;
    private Boolean digitalSigningEnabled;
}