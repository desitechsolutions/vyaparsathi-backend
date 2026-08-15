package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Unit-of-measure conversion factor. {@code fromUnit} × {@code factor} =
 * {@code toUnit}. Shop-scoped rows override the global rows (shop_id NULL).
 */
@Entity
@Table(name = "uom_conversion")
@Getter
@Setter
@NoArgsConstructor
public class UomConversion extends ShopAwareEntity {

    @Column(name = "from_unit", nullable = false, length = 20)
    private String fromUnit;

    @Column(name = "to_unit", nullable = false, length = 20)
    private String toUnit;

    @Column(name = "factor", nullable = false, precision = 14, scale = 6)
    private BigDecimal factor;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
