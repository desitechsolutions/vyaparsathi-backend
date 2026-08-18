package com.desitech.vyaparsathi.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(regexp = "^$|^[0-9]{2}$", message = "State code must be a 2-digit GST state code (e.g. 27 for Maharashtra)")
    private String stateCode;

    /**
     * 15-character GSTIN. Format: 2-digit state code + 10-char PAN + entity
     * number + 'Z' + check-digit. Blank is allowed since not every shop is
     * GST-registered yet.
     */
    @Pattern(
            regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$",
            message = "GSTIN must be a valid 15-character GST identification number."
    )
    private String gstin;

    @NotBlank(message = "Shop code is required")
    @Pattern(
            regexp = "^[a-z0-9][a-z0-9-]{1,49}$",
            message = "Shop code must be lowercase letters, digits or hyphens (2-50 chars, starting with a letter or digit)."
    )
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

    /**
     * 10-character PAN. Format: 5 letters + 4 digits + 1 letter. Blank allowed
     * for individuals / non-PAN-holding shops during onboarding.
     */
    @Pattern(
            regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]{1}$",
            message = "PAN must be a valid 10-character permanent account number."
    )
    private String pan;

    /** 21-char CIN for Indian companies. Blank allowed for non-corporate entities. */
    @Pattern(
            regexp = "^$|^[LU][0-9]{5}[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{6}$",
            message = "CIN must be a valid 21-character corporate identity number."
    )
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

    /** Phase 5 policy: OWNER + ADMIN must have MFA enabled to sign in. */
    private Boolean requireMfaForAdmins;
}