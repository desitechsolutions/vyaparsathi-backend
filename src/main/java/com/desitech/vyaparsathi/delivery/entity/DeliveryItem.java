package com.desitech.vyaparsathi.delivery.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line on a Delivery Challan — records what qty of a specific
 * {@link SaleItem} was dispatched in this shipment. Enables partial
 * fulfillment: a sale of 10 units can be delivered as two shipments of 5.
 *
 * <p>{@link #saleItem} may be null when the delivery is bootstrapped
 * automatically for a legacy delivery record that had no per-line breakdown.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "delivery_item")
public class DeliveryItem extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false)
    @JsonBackReference
    private Delivery delivery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_item_id")
    private SaleItem saleItem;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "hsn_sac", length = 20)
    private String hsnSac;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "qty", nullable = false, precision = 10, scale = 2)
    private BigDecimal qty;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;
}
