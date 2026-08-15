package com.desitech.vyaparsathi.shop.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ShopBankAccountDto {
    private Long id;
    private String label;
    private String accountHolderName;
    private String accountNumber;
    private String bankName;
    private String ifscCode;
    private String branch;
    private String accountType;
    private String currencyCode = "INR";
    private String swiftCode;
    private String iban;
    private String upiId;
    private String purpose;
    private Boolean isDefault = Boolean.FALSE;
    private Boolean isActive = Boolean.TRUE;
    private Boolean displayOnInvoice = Boolean.TRUE;
    private String notes;
}
