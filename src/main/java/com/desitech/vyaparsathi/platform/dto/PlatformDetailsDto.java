package com.desitech.vyaparsathi.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformDetailsDto {

    private Long id;

    @NotBlank(message = "Company Name is required")
    private String companyName;

    private String tradeName;
    private String gstin;
    private String pan;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String stateCode;
    private String pincode;

    @Email(message = "Invalid Support Email format")
    private String supportEmail;

    private String supportPhone;
    private String hsnSacCode;
    private String invoicePrefix;

    private String bankName;
    private String accountNumber;
    private String ifscCode;
    private String upiId;
}
