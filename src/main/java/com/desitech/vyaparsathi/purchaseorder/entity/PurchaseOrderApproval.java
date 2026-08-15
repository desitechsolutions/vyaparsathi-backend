package com.desitech.vyaparsathi.purchaseorder.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Multi-level approval step for a PO. Mirrors {@link com.desitech.vyaparsathi
 * .receiving.entity.ReceivingApproval} — same threshold + delegate pattern so
 * enterprise dashboards can join the two tables for a unified queue.
 */
@Entity
@Table(name = "purchase_order_approval")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderApproval extends ShopAwareEntity {

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "level", nullable = false)
    private Short level;

    @Column(name = "approver_role", length = 50)
    private String approverRole;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "delegate_id")
    private Long delegateId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "threshold_min", precision = 14, scale = 2)
    private BigDecimal thresholdMin;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
