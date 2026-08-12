package com.desitech.vyaparsathi.receipt.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Per-shop / per-prefix / per-fiscal-year counter for payment receipt numbers.
 * Mirrors {@code InvoiceSequence} exactly — same pessimistic-lock pattern, same
 * fiscal-year semantics. Not shop-aware in the JPA sense (raw {@code shopId}
 * column) because the counter is contended and must not go through the Hibernate
 * shop filter.
 */
@Entity
@Table(name = "payment_receipt_sequence")
@Getter
@Setter
@NoArgsConstructor
public class PaymentReceiptSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "prefix", nullable = false, length = 100)
    private String prefix = "RCP";

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
