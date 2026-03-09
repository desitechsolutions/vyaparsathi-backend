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
     * Drug License number for pharmacy suppliers.
     * Required for pharmacy regulatory compliance and purchase register entries.
     */
    @Column(name = "drug_license_number")
    private String drugLicenseNumber;
}
