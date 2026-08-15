package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Soft-hold on inventory. A reservation reduces the sellable quantity for an
 * item variant without moving stock — used by sales orders / quotations that
 * commit later. When the sale finalizes we {@code CONSUME} the reservation
 * (stock deducts normally); when the sale expires or cancels we {@code RELEASE}
 * it (qty returns to the sellable pool). Nothing here mutates {@link
 * StockMovement} rows directly.
 */
@Entity
@Table(name = "inventory_reservation")
@Getter
@Setter
@NoArgsConstructor
public class InventoryReservation extends ShopAwareEntity {

    @Column(name = "item_variant_id", nullable = false)
    private Long itemVariantId;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    /** SALES_ORDER / QUOTATION / MANUAL_HOLD / etc. */
    @Column(name = "reason", nullable = false, length = 50)
    private String reason;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reserved_by", length = 100)
    private String reservedBy;

    @Column(name = "reserved_at", nullable = false)
    private LocalDateTime reservedAt = LocalDateTime.now();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** ACTIVE / CONSUMED / RELEASED / EXPIRED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "notes", length = 500)
    private String notes;
}
