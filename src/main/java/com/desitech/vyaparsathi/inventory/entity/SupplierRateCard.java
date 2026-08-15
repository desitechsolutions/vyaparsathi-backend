package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Contracted rate for a specific (supplier, item variant, currency) pair,
 * valid within a date window. PO creation can pre-fill unit cost from the
 * active rate card row; downstream analytics compare captured cost to contract.
 */
@Entity
@Table(name = "supplier_rate_card")
@Getter
@Setter
@NoArgsConstructor
public class SupplierRateCard extends ShopAwareEntity {

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "item_variant_id", nullable = false)
    private Long itemVariantId;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "min_order_qty", precision = 12, scale = 3)
    private BigDecimal minOrderQty;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
