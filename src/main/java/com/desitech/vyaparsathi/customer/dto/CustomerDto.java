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

    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime createdAt;
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getGstNumber() { return gstNumber; }
    public void setGstNumber(String gstNumber) { this.gstNumber = gstNumber; }

    public String getPanNumber() { return panNumber; }
    public void setPanNumber(String panNumber) { this.panNumber = panNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getCreditBalance() { return creditBalance; }
    public void setCreditBalance(BigDecimal creditBalance) { this.creditBalance = creditBalance; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }

    public Integer getCreditDays() { return creditDays; }
    public void setCreditDays(Integer creditDays) { this.creditDays = creditDays; }

    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }

    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getTradeName() { return tradeName; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public LocalDate getAnniversaryDate() { return anniversaryDate; }
    public void setAnniversaryDate(LocalDate anniversaryDate) { this.anniversaryDate = anniversaryDate; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}