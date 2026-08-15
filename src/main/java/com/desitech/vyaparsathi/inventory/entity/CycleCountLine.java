package com.desitech.vyaparsathi.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cycle_count_line")
@Getter
@Setter
@NoArgsConstructor
public class CycleCountLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_count_id", nullable = false)
    private CycleCount cycleCount;

    @Column(name = "item_variant_id", nullable = false)
    private Long itemVariantId;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "system_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal systemQty = BigDecimal.ZERO;

    @Column(name = "counted_qty", precision = 12, scale = 3)
    private BigDecimal countedQty;

    @Column(name = "variance_qty", precision = 12, scale = 3)
    private BigDecimal varianceQty;

    @Column(name = "reason")
    private String reason;

    @Column(name = "counted_by", length = 100)
    private String countedBy;

    @Column(name = "counted_at")
    private LocalDateTime countedAt;
}
