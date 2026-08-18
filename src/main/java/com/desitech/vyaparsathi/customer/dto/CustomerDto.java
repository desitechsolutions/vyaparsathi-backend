package com.desitech.vyaparsathi.customer.dto;

import com.desitech.vyaparsathi.common.util.CustomLocalDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CustomerDto {
    private Long id;

    @NotBlank(message = "Customer name is required")
    @Size(max = 255, message = "Name must be 255 chars or less")
    private String name;

    @Pattern(regexp = "^$|^\\d{10}$", message = "Phone must be exactly 10 digits")
    private String phone;

    @Email(message = "Invalid email address")
    private String email;

    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;

    /** 2-digit GSTN state code (e.g. "27" for Maharashtra). */
    private String stateCode;
    private String postalCode;
    private String country;
    private String gstNumber;
    private String panNumber;
    private String notes;
    private BigDecimal creditBalance;

    // ─── V99 statutory identity ────────────────────────────────────────
    private String legalName;

    // ─── V104 CRM + lifecycle ──────────────────────────────────────────
    private Boolean active;
    private String customerType;
    private Integer creditDays;
    private BigDecimal creditLimit;
    private String paymentTerms;
    private String tags;
    private String tradeName;
    private LocalDate dateOfBirth;
    private LocalDate anniversaryDate;
    private String industry;
    private String source;

    // ─── V115 enterprise fields ────────────────────────────────────────
    /** When TRUE, invoice creation is blocked by the credit-limit gate. */
    private Boolean creditHold;
    /** Sales rep / account manager (users.id). */
    private Long assignedUserId;
    /** ISO-4217 currency code; defaults to INR. */
    private String preferredCurrency;
    private String msmeUdyam;
    private String tan;
    private Boolean tdsApplicable;
    private Boolean emailOptIn;
    private Boolean smsOptIn;
    private Boolean whatsappOptIn;

    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime createdAt;
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime updatedAt;

    // Manual getters/setters were previously duplicated for every field
    // alongside Lombok's @Data. All pass-throughs removed — Lombok now
    // generates them (which was already the effective behaviour anyway,
    // since Lombok skipped fields with a matching hand-written accessor).
}