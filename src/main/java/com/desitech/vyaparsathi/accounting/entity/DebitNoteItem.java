package com.desitech.vyaparsathi.accounting.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturnItem;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One line on a {@link DebitNote}. Snapshots a returned
 * {@link PurchaseReturnItem}. Note that purchase returns currently carry only
 * pre-tax cost (no GST split), so the GST columns default to zero — they exist
 * for a later migration that adds GST breakdown on purchase-side lines.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "debit_note_item")
public class DebitNoteItem extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debit_note_id", nullable = false)
    @JsonBackReference
    private DebitNote debitNote;

    /** Nullable — a manual (non-return) debit note may not reference a specific return item. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_return_item_id")
    private PurchaseReturnItem purchaseReturnItem;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "hsn_sac", length = 20)
    private String hsnSac;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "qty", nullable = false, precision = 10, scale = 2)
    private BigDecimal qty;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "taxable_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableValue;

    @Column(name = "cgst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal cgstAmt = BigDecimal.ZERO;

    @Column(name = "sgst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal sgstAmt = BigDecimal.ZERO;

    @Column(name = "igst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal igstAmt = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // ─── V132 GST Phase 1 additions ──────────────────────────────────────────

    /** Compensation cess rate (%) on this debit-note line. Zero for non-cess goods. */
    @Column(name = "cess_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal cessRate = BigDecimal.ZERO;

    /** Cess amount = taxableValue × cessRate / 100. */
    @Column(name = "cess_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal cessAmt = BigDecimal.ZERO;

    public BigDecimal getCessRate() { return cessRate; }
    public void setCessRate(BigDecimal cessRate) {
        this.cessRate = cessRate != null ? cessRate : BigDecimal.ZERO;
    }

    public BigDecimal getCessAmt() { return cessAmt; }
    public void setCessAmt(BigDecimal cessAmt) {
        this.cessAmt = cessAmt != null ? cessAmt : BigDecimal.ZERO;
    }
}
