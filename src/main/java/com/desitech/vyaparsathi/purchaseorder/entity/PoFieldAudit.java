package com.desitech.vyaparsathi.purchaseorder.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Field-level audit trail for a PO. One row per changed column; captures the
 * old + new value and the user who committed the change. Sits alongside
 * {@link PurchaseOrderStatusHistory} which tracks status transitions.
 */
@Entity
@Table(name = "po_field_audit")
@Getter
@Setter
@NoArgsConstructor
public class PoFieldAudit extends ShopAwareEntity {

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "field_name", nullable = false, length = 80)
    private String fieldName;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();
}
