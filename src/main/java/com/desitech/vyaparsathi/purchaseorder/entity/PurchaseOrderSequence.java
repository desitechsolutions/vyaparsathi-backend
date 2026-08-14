package com.desitech.vyaparsathi.purchaseorder.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Per-shop, per-fiscal-year sequence for purchase-order numbering. Mirrors
 * {@code InvoiceSequence} / {@code QuotationSequence} — same pessimistic-lock
 * pattern that keeps two concurrent creates from collision. Numbers are
 * rendered as {@code PO/YY-YY/NNNNN}.
 */
@Entity
@Table(name = "purchase_order_sequence")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "prefix", nullable = false, length = 100)
    private String prefix = "PO";

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