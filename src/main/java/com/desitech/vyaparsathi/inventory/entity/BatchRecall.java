package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch recall header. Locks any remaining stock of the recalled batch and
 * fans out impact records for every outbound movement so operators know who
 * received the affected units.
 */
@Entity
@Table(name = "batch_recall")
@Getter
@Setter
@NoArgsConstructor
public class BatchRecall extends ShopAwareEntity {

    @Column(name = "batch_number", nullable = false, length = 100)
    private String batchNumber;

    @Column(name = "item_variant_id")
    private Long itemVariantId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    /** OPEN / IN_PROGRESS / CLOSED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "initiated_by", length = 100)
    private String initiatedBy;

    @Column(name = "initiated_at", nullable = false)
    private LocalDateTime initiatedAt = LocalDateTime.now();

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "supplier_notified", nullable = false)
    private boolean supplierNotified = false;

    @Column(name = "customers_notified", nullable = false)
    private boolean customersNotified = false;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "batchRecall", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BatchRecallImpact> impacts = new ArrayList<>();
}
