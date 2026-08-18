package com.desitech.vyaparsathi.accounting.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row per "credit note X → invoice Y" application, or per cash/bank
 * refund payout for a credit note. Making applied_amount an auditable
 * running total instead of a floating scalar. Mirrors {@code DebitNoteApplication}.
 *
 * <p>The tenant filter aspect applies via {@link ShopAwareEntity} so this
 * table is transparently shop-scoped like every other business row.
 */
@Entity
@Table(name = "credit_note_allocation")
@Getter
@Setter
@NoArgsConstructor
public class CreditNoteAllocation extends ShopAwareEntity {

    /** {@code INVOICE} = applied against a sale, {@code REFUND} = paid back. */
    public enum Type { INVOICE, REFUND }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_note_id", nullable = false)
    private CreditNote creditNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_type", nullable = false, length = 20)
    private Type allocationType;

    /** Populated only when {@link #allocationType} is INVOICE. */
    @Column(name = "sale_id")
    private Long saleId;

    @Column(name = "payment_mode", length = 30)
    private String paymentMode;

    @Column(name = "payment_reference", length = 120)
    private String paymentReference;

    @Column(name = "allocated_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedAmount = BigDecimal.ZERO;

    @Column(name = "allocated_at", nullable = false)
    private LocalDateTime allocatedAt = LocalDateTime.now();

    @Column(name = "allocated_by", length = 120)
    private String allocatedBy;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "reversed", nullable = false)
    private Boolean reversed = Boolean.FALSE;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "reversed_by", length = 120)
    private String reversedBy;
}
