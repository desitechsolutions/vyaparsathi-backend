package com.desitech.vyaparsathi.supplier.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "supplier")
@Getter
@Setter
@NoArgsConstructor
public class Supplier extends ShopAwareEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_person")
    private String contactPerson;

    @Column
    private String phone;

    @Column
    private String email;

    @Column
    private String address;

    @Column
    private String gstin;

    /**
     * 2-digit GSTN state code (01–38, 97). Drives intra/inter-state tax
     * decisions on inward invoices. May also be derived from the first
     * two characters of gstin when the supplier is GST-registered.
     */
    @Column(name = "state_code", length = 2)
    private String stateCode;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSupplierName() { return name; }

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

    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
}
