package com.desitech.vyaparsathi.customer.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * EAV cell storing a per-customer custom-attribute value. The
 * catalogue of field definitions ("Preferred delivery day", "Client
 * Priority Tier", …) lives in the existing {@code custom_attributes}
 * table (Phase 4) — this table only holds the values keyed by
 * {@code fieldKey}.
 *
 * <p>Unique (customer_id, field_key) so a customer never carries two
 * values for the same field.</p>
 */
@Entity
@Table(
    name = "customer_custom_field_value",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_customer_cfv_customer_field",
            columnNames = {"customer_id", "field_key"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class CustomerCustomFieldValue extends ShopAwareEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;
}
