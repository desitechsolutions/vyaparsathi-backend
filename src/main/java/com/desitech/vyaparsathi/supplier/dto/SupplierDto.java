package com.desitech.vyaparsathi.supplier.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SupplierDto {
    private Long id;
    private String name;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String gstin;

    /** 2-digit GSTN state code (01–38, 97). May be derived from gstin[0..1] when unset. */
    private String stateCode;

    // ─── V99 statutory identity ────────────────────────────────────────
    private String legalName;
    private String tradeName;
    private String pan;
    private String state;
    private String city;
    private String pincode;
    private String country;

    // ─── V103 lifecycle + AP terms ─────────────────────────────────────
    private Boolean active;
    private Integer creditDays;
    private BigDecimal creditLimit;
    private String paymentTerms;
    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSupplierName() { return name; }
    public void setSupplierName(String supplierName) { this.name = supplierName; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public String getGstNumber() { return gstin; }
    public void setGstNumber(String gstNumber) { this.gstin = gstNumber; }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getTradeName() { return tradeName; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }
    public String getPan() { return pan; }
    public void setPan(String pan) { this.pan = pan; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Integer getCreditDays() { return creditDays; }
    public void setCreditDays(Integer creditDays) { this.creditDays = creditDays; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }
    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
