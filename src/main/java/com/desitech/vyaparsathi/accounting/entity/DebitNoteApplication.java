package com.desitech.vyaparsathi.accounting.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Audit row for every apply-against-invoice event on a debit note. The parent
 * {@link DebitNote#getAppliedAmount()} is the running SUM of non-reversed
 * applications — Finance can trace every rupee of applied amount back to a
 * specific purchase invoice + user + timestamp.
 */
@Entity
@Table(name = "debit_note_application")
@Getter
@Setter
@NoArgsConstructor
public class DebitNoteApplication extends ShopAwareEntity {

    @Column(name = "debit_note_id", nullable = false)
    private Long debitNoteId;

    /** Which supplier invoice this DN was netted against. Optional — a DN may also apply against a supplier payment run. */
    @Column(name = "purchase_invoice_id")
    private Long purchaseInvoiceId;

    /** Alternative apply target: a specific supplier payment row. */
    @Column(name = "supplier_payment_id")
    private Long supplierPaymentId;

    @Column(name = "applied_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal appliedAmount;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt = LocalDateTime.now();

    @Column(name = "applied_by", length = 100)
    private String appliedBy;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "reversed", nullable = false)
    private boolean reversed = false;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "reversed_by", length = 100)
    private String reversedBy;
}
