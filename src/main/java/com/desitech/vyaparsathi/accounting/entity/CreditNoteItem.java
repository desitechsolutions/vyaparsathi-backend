package com.desitech.vyaparsathi.accounting.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.sales.enums.GSTType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One line on a {@link CreditNote}. Typically snapshots a returned
 * {@link SaleItem} with the pro-rated qty/amounts at the time the return
 * was processed. Free-text lines are supported ({@link #saleItem} is nullable).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "credit_note_item")
public class CreditNoteItem extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_note_id", nullable = false)
    @JsonBackReference
    private CreditNote creditNote;

    /** Nullable — a manual (non-return) credit note may not reference a specific sale item. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_item_id")
    private SaleItem saleItem;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "hsn_sac", length = 20)
    private String hsnSac;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "qty", nullable = false, precision = 10, scale = 2)
    private BigDecimal qty;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

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

    /**
     * GST rate slab for this credit-note line.
     * Previously absent, forcing {@code GstTaxService.buildTaxItemsFromNote()} to
     * back-derive the rate from the amounts ratio — a computation that is lossy for
     * nil-rated items and fails on divide-by-zero. Populated at credit-note creation
     * from the originating {@link SaleItem#getGstType()}.
     * Default {@link GSTType#GST_0} is safe for existing rows (nil/exempt lines).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gst_type", nullable = false, length = 20)
    private GSTType gstType = GSTType.GST_0;

    /** Compensation cess rate (%) on this line. Zero for non-cess goods. */
    @Column(name = "cess_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal cessRate = BigDecimal.ZERO;

    /** Cess amount = taxableValue × cessRate / 100. Feeds GSTR-1 CDNR {@code csamt}. */
    @Column(name = "cess_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal cessAmt = BigDecimal.ZERO;

    public GSTType getGstType() { return gstType; }
    public void setGstType(GSTType gstType) {
        this.gstType = gstType != null ? gstType : GSTType.GST_0;
    }

    public BigDecimal getCessRate() { return cessRate; }
    public void setCessRate(BigDecimal cessRate) {
        this.cessRate = cessRate != null ? cessRate : BigDecimal.ZERO;
    }

    public BigDecimal getCessAmt() { return cessAmt; }
    public void setCessAmt(BigDecimal cessAmt) {
        this.cessAmt = cessAmt != null ? cessAmt : BigDecimal.ZERO;
    }
}
