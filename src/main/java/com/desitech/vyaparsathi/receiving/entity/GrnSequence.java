package com.desitech.vyaparsathi.receiving.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Per-shop, per-fiscal-year sequence for GRN numbering. Mirrors
 * {@code PurchaseOrderSequence} — same pessimistic-lock pattern that keeps
 * two concurrent GRN creates from colliding. Numbers render as
 * {@code GRN/YY-YY/NNNNN}.
 */
@Entity
@Table(name = "grn_sequence")
@Getter
@Setter
@NoArgsConstructor
public class GrnSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "prefix", nullable = false, length = 100)
    private String prefix = "GRN";

    @Column(name = "fiscal_year", nullable = false)
    private short fiscalYear;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}