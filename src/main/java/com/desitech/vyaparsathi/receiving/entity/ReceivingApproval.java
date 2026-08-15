package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Multi-level approval step for a GRN. Level 1 stamps go first, level 2
 * activates only after L1 completes when the GRN total crosses
 * {@code threshold_min}. Threshold rules keep low-value receipts frictionless
 * while forcing scrutiny on high-value ones.
 */
@Entity
@Table(name = "receiving_approval")
@Getter
@Setter
@NoArgsConstructor
public class ReceivingApproval extends ShopAwareEntity {

    @Column(name = "receiving_id", nullable = false)
    private Long receivingId;

    @Column(name = "level", nullable = false)
    private Short level;

    @Column(name = "approver_role", length = 50)
    private String approverRole;

    @Column(name = "approver_id")
    private Long approverId;

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
