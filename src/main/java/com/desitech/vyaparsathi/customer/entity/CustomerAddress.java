package com.desitech.vyaparsathi.customer.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Structured address for a customer. Multiple rows per customer are
 * expected — a business often has one legal / registered address, one
 * or more shipping addresses (per warehouse / branch), and a distinct
 * billing address. {@code addressType} names the primary intent;
 * {@code isDefaultBilling} / {@code isDefaultShipping} are the flags
 * the invoice-creation flow reads to pre-fill bill-to / ship-to.
 */
@Entity
@Table(name = "customer_address")
@Getter
@Setter
@NoArgsConstructor
public class CustomerAddress extends ShopAwareEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** BILLING · SHIPPING · REGISTERED — free-text so shops can add their own. */
    @Column(name = "address_type", nullable = false, length = 20)
    private String addressType = "BILLING";

    /** Human-readable label, e.g. "Head Office", "Bengaluru warehouse". */
    @Column(length = 120)
    private String label;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(length = 120)
    private String city;

    @Column(length = 120)
    private String state;

    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 120)
    private String country = "India";

    @Column(name = "is_default_billing", nullable = false)
    private Boolean isDefaultBilling = Boolean.FALSE;

    @Column(name = "is_default_shipping", nullable = false)
    private Boolean isDefaultShipping = Boolean.FALSE;

    public Boolean getIsDefaultBilling() { return isDefaultBilling != null && isDefaultBilling; }
    public void setIsDefaultBilling(Boolean isDefaultBilling) { this.isDefaultBilling = isDefaultBilling != null && isDefaultBilling; }

    public Boolean getIsDefaultShipping() { return isDefaultShipping != null && isDefaultShipping; }
    public void setIsDefaultShipping(Boolean isDefaultShipping) { this.isDefaultShipping = isDefaultShipping != null && isDefaultShipping; }
}
