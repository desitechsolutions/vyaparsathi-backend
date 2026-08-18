package com.desitech.vyaparsathi.customer.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Normalised replacement for the CSV {@code customer.tags} column.
 * A segment is a shop-scoped label ("Wholesale", "VIP", "Overdue &gt;
 * 60d", "GST-Registered") that groups customers for reporting,
 * bulk actions, and marketing campaigns.
 *
 * <p>Unique per (shop_id, name) — case-insensitive uniqueness is
 * enforced at the service layer.</p>
 */
@Entity
@Table(
    name = "customer_segment",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_segment_shop_name", columnNames = {"shop_id", "name"})
    }
)
@Getter
@Setter
@NoArgsConstructor
public class CustomerSegment extends ShopAwareEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;

    /** Hex code for the FE chip — e.g. "#F59E0B". Null → auto-picked from hash. */
    @Column(length = 16)
    private String color;
}
