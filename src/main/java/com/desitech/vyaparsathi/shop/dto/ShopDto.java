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

    private String gstin;

    @NotBlank(message = "Shop code is required")
    private String code;

    private String locale;

    @NotBlank(message = "Industry type is required")
    private String industryType;

    // --- New Fields for Settings ---
    private String phone;
    private String email;

    private String logoPath;
    private String signaturePath;

    private Boolean isCompositionScheme;
    private String brandColor;

    private String termsAndConditions;
    private String bankDetails;

    /** Drug license number for pharmacy shops (shown on pharmacy invoices). */
    private String drugLicenseNumber;
}