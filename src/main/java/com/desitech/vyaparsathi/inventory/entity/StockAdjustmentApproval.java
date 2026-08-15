package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Approval envelope for a stock adjustment. Any ADJUST whose absolute
 * (delta × WAC) crosses the shop's {@code adjustmentApprovalThreshold} is
 * held in {@code PENDING} status and only committed once an OWNER role
 * flips it to {@code APPROVED}.
 */
@Entity
@Table(name = "stock_adjustment_approval")
@Getter
@Setter
@NoArgsConstructor
public class StockAdjustmentApproval extends ShopAwareEntity {

    @Column(name = "item_variant_id", nullable = false)
    private Long itemVariantId;

    @Column(name = "delta_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal deltaQty;

    @Column(name = "delta_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal deltaValue = BigDecimal.ZERO;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** PENDING / APPROVED / REJECTED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "note", length = 500)
    private String note;
}
