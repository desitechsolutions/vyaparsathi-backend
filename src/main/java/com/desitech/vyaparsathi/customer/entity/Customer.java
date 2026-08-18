package com.desitech.vyaparsathi.customer.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends ShopAwareEntity {
    @Column(nullable = false)
    private String name;

    @Column(length = 15, unique = true)
    private String phone;

    private String email;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;

    @Column(name = "state_code", length = 2)
    private String stateCode;

    private String postalCode;
    private String country;
    private String gstNumber;
    private String panNumber;
    private String notes;

    @Column(name = "credit_balance", nullable = false)
    private BigDecimal creditBalance = BigDecimal.ZERO;

    /** V99 — legal name (defaults to trade `name` when unset). */
    @Column(name = "legal_name")
    private String legalName;

    // ─── V104 CRM + lifecycle ─────────────────────────────────────────
    @Column(name = "active", nullable = false)
    private Boolean active = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private com.desitech.vyaparsathi.customer.enums.CustomerType customerType =
            com.desitech.vyaparsathi.customer.enums.CustomerType.INDIVIDUAL;

    /** Default net-N receivable terms in days. */
    @Column(name = "credit_days")
    private Integer creditDays;

    /** Max outstanding receivable we'll extend before flagging. */
    @Column(name = "credit_limit", precision = 14, scale = 2)
    private BigDecimal creditLimit;

    /** Free-text override — e.g. "COD only", "NET15", "30 days from GRN". */
    @Column(name = "payment_terms", length = 200)
    private String paymentTerms;

    /** Comma-separated tags for CRM segmentation. */
    @Column(name = "tags", length = 500)
    private String tags;

    /** Display name distinct from legal — e.g. "Acme" vs "Acme Traders Pvt Ltd". */
    @Column(name = "trade_name")
    private String tradeName;

    /** Individual customer's birthday — birthday-marketing hook. */
    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    /** Anniversary date (wedding / business incorporation). */
    @Column(name = "anniversary_date")
    private java.time.LocalDate anniversaryDate;

    /** Free-text industry for B2B customers. */
    @Column(name = "industry", length = 100)
    private String industry;

    /** Where the customer came from — WALK_IN / REFERRAL / ONLINE / MARKETING / OTHER. */
    @Column(name = "source", length = 30)
    private String source;

    public Boolean getActive() { return active == null || active; }
    public void setActive(Boolean active) { this.active = active != null && active; }

    public com.desitech.vyaparsathi.customer.enums.CustomerType getCustomerType() {
        return customerType == null
                ? com.desitech.vyaparsathi.customer.enums.CustomerType.INDIVIDUAL
                : customerType;
    }
    public void setCustomerType(com.desitech.vyaparsathi.customer.enums.CustomerType customerType) {
        this.customerType = customerType;
    }

    public Integer getCreditDays() { return creditDays; }
    public void setCreditDays(Integer creditDays) { this.creditDays = creditDays; }

    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }

    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getTradeName() { return tradeName != null ? tradeName : name; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }

    public java.time.LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(java.time.LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public java.time.LocalDate getAnniversaryDate() { return anniversaryDate; }
    public void setAnniversaryDate(java.time.LocalDate anniversaryDate) { this.anniversaryDate = anniversaryDate; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getLegalName() { return legalName != null ? legalName : name; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

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

    public String getGstin() { return gstNumber; }

    public String getPanNumber() { return panNumber; }
    public void setPanNumber(String panNumber) { this.panNumber = panNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getCreditBalance() { return creditBalance; }
    public void setCreditBalance(BigDecimal creditBalance) { this.creditBalance = creditBalance; }
}