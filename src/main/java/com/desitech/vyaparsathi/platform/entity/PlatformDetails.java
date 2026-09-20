package com.desitech.vyaparsathi.platform.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "platform_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "cin", length = 30)
    private String cin;

    @Column(name = "gstin", length = 20)
    private String gstin;

    @Column(name = "pan", length = 20)
    private String pan;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "state_code", length = 10)
    private String stateCode;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "support_email")
    private String supportEmail;

    @Column(name = "support_phone", length = 30)
    private String supportPhone;

    @Column(name = "hsn_sac_code", length = 20)
    private String hsnSacCode;

    @Column(name = "invoice_prefix", length = 20)
    private String invoicePrefix;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "ifsc_code", length = 20)
    private String ifscCode;

    @Column(name = "upi_id", length = 100)
    private String upiId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
