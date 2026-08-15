package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Supplier invoice ledger row. Populated at 3-way match time from either the
 * FE (manual capture from supplier's PDF) or a future OCR pipeline. One
 * invoice may match one GRN (the common case) or multiple GRNs — this schema
 * keeps the FK optional so unmatched invoices can be staged.
 *
 * <p>Match statuses: UNMATCHED / MATCHED / VARIANCE / DISPUTED — the FE surfaces
 * the delta chip on the GRN detail page based on this value.
 */
@Entity
@Table(name = "ap_invoice")
@Getter
@Setter
@NoArgsConstructor
public class ApInvoice extends ShopAwareEntity {

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "receiving_id")
    private Long receivingId;

    @Column(name = "invoice_no", nullable = false, length = 100)
    private String invoiceNo;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "payment_terms_days")
    private Integer paymentTermsDays = 30;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "match_status", nullable = false, length = 30)
    private String matchStatus = "UNMATCHED";

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    @Column(name = "matched_by")
    private Long matchedBy;

    @Column(name = "variance_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal varianceAmount = BigDecimal.ZERO;

    @Column(name = "variance_note", length = 500)
    private String varianceNote;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
