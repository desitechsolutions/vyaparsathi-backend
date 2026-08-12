package com.desitech.vyaparsathi.salesorder.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A stock reservation held against a {@link SalesOrder}. Each row represents
 * qty that has been "committed" to an approved order and should not be sold
 * to another walk-in customer. Physical stock is NOT touched — reservations
 * only affect the {@code available_stock = current_stock - Σ reserved_qty}
 * projection.
 *
 * <p>Rows are created when an SO is APPROVED, decremented on partial
 * conversion, and deleted (via cascade on parent SO delete or explicit release)
 * on cancellation or full fulfillment.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "stock_reservation")
public class StockReservation extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_variant_id", nullable = false)
    private ItemVariant itemVariant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_item_id")
    private SalesOrderItem salesOrderItem;

    @Column(name = "reserved_qty", nullable = false, precision = 10, scale = 2)
    private BigDecimal reservedQty;
}
