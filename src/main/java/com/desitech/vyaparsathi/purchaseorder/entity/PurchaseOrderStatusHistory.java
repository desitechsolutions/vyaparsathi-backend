package com.desitech.vyaparsathi.purchaseorder.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row per PO status transition. Mirrors {@code ReceivingStatusHistory}
 * (V90) so the audit trail on POs looks identical to GRNs — compliance
 * dashboards can pivot both by the same query shape.
 */
@Entity
@Table(name = "purchase_order_status_history")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderStatusHistory extends ShopAwareEntity {

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    @Column(name = "note", length = 500)
    private String note;
}
