package com.desitech.vyaparsathi.customer.dto;

import com.desitech.vyaparsathi.common.util.CustomLocalDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    @Pattern(regexp = "^$|^\\d{2}$", message = "State code must be exactly 2 numeric digits (e.g. '27')")
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

    public void setPhone(String phone) {
        if (phone != null) {
            String digits = phone.replaceAll("\\D", "");
            if (digits.length() == 12 && digits.startsWith("91")) {
                digits = digits.substring(2);
            } else if (digits.length() == 11 && digits.startsWith("0")) {
                digits = digits.substring(1);
            }
            this.phone = digits.isEmpty() ? null : digits;
        } else {
            this.phone = null;
        }
    }

    public void setEmail(String email) {
        this.email = (email != null && !email.isBlank()) ? email.trim() : null;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = (stateCode != null && !stateCode.isBlank()) ? stateCode.trim() : null;
    }

    public void setGstNumber(String gstNumber) {
        this.gstNumber = (gstNumber != null && !gstNumber.isBlank()) ? gstNumber.trim().toUpperCase() : null;
    }

    public void setPanNumber(String panNumber) {
        this.panNumber = (panNumber != null && !panNumber.isBlank()) ? panNumber.trim().toUpperCase() : null;
    }
}