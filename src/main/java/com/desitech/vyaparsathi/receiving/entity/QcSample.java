package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * QC / AQL sample record. Captures inspected sample size, defects found, and
 * verdict (PASS / FAIL / PENDING). The AQL % is computed as
 * defects / sample_size × 100.
 */
@Entity
@Table(name = "qc_sample")
@Getter
@Setter
@NoArgsConstructor
public class QcSample extends ShopAwareEntity {

    @Column(name = "receiving_id")
    private Long receivingId;

    @Column(name = "receiving_item_id")
    private Long receivingItemId;

    @Column(name = "item_variant_id", nullable = false)
    private Long itemVariantId;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;

    @Column(name = "defects_found", nullable = false)
    private Integer defectsFound = 0;

    @Column(name = "aql_pct", precision = 6, scale = 3)
    private BigDecimal aqlPct;

    /** PASS / FAIL / PENDING */
    @Column(name = "verdict", nullable = false, length = 20)
    private String verdict = "PENDING";

    @Column(name = "inspected_by", length = 100)
    private String inspectedBy;

    @Column(name = "inspected_at", nullable = false)
    private LocalDateTime inspectedAt = LocalDateTime.now();

    @Column(name = "notes", length = 500)
    private String notes;
}
