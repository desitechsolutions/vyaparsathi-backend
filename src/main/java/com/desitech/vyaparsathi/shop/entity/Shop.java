package com.desitech.vyaparsathi.shop.entity;

import com.desitech.vyaparsathi.common.util.LocalDateTimeAttributeConverter;
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

    @Column(name = "industry_type")
    private String industryType;

    @Column(name = "is_composition_scheme")
    private Boolean isCompositionScheme = false;

    @Column(name = "brand_color")
    private String brandColor = "#2980b9";

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
     * Drug license number for pharmacy shops.
     * Displayed on pharmacy invoices as required by regulatory compliance.
     */
    @Column(name = "drug_license_number")
    private String drugLicenseNumber;
}