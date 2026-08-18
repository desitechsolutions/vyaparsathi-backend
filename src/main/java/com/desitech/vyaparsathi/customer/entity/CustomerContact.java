package com.desitech.vyaparsathi.customer.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Additional contact person for a customer — a business commonly has
 * separate purchase / accounts / delivery contacts. Exactly one row
 * per customer is flagged {@code isPrimary}, enforced at the service
 * layer (invariant: one primary contact per customer at all times).
 */
@Entity
@Table(name = "customer_contact")
@Getter
@Setter
@NoArgsConstructor
public class CustomerContact extends ShopAwareEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 120)
    private String designation;

    @Column(length = 30)
    private String phone;

    @Column(length = 200)
    private String email;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = Boolean.FALSE;

    @Column(length = 500)
    private String notes;

    public Boolean getIsPrimary() { return isPrimary != null && isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary != null && isPrimary; }
}
