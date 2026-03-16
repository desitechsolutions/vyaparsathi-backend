package com.desitech.vyaparsathi.supplier.dto;

import lombok.Data;

@Data
public class SupplierDto {
    private Long id;
    private String name;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String gstin;
    /** Drug License number (for pharmacy suppliers). Required for purchase register compliance. */
    private String drugLicenseNumber;
}
