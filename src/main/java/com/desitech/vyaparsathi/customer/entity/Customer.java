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