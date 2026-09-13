package com.desitech.vyaparsathi.purchases.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.gst.enums.GstnUqc;
import com.desitech.vyaparsathi.gst.enums.LineType;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.sales.enums.GSTType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "purchase_invoice_items")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseInvoiceItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id", nullable = false)
    @JsonBackReference
    private PurchaseInvoice purchaseInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_variant_id")
    private ItemVariant itemVariant;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "hsn_code", length = 20)
    private String hsnCode;

    @Column(name = "batch_number", length = 50)
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "discount", precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "taxable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableAmount;

    /**
     * GST rate as a typed enum — aligns with the sales-side {@link GSTType}.
     * Previously this was stored only as a raw {@link BigDecimal} {@code gst_rate},
     * which allowed invalid rates to be persisted silently. The enum provides
     * type-safety at the JPA layer while the raw column is retained for legacy queries.
     * Use {@link #getGstTypeEnum()} for all new computation; the raw
     * {@code gstRate} field remains for backwards-compatible DTO mapping.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gst_type", length = 20)
    private GSTType gstTypeEnum;

    @Column(name = "gst_rate", precision = 5, scale = 2)
    private BigDecimal gstRate = BigDecimal.ZERO;

    @Column(name = "cgst_amount", precision = 12, scale = 2)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(name = "sgst_amount", precision = 12, scale = 2)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(name = "igst_amount", precision = 12, scale = 2)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    /** UTGST — populated only for intra-UT purchases (shop and supplier both in the same Union Territory). */
    @Column(name = "utgst_amount", precision = 12, scale = 2)
    private BigDecimal utgstAmount = BigDecimal.ZERO;

    /** Compensation Cess rate (%) — applicable to cess-liable inward supplies. */
    @Column(name = "cess_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal cessRate = BigDecimal.ZERO;

    /** Cess amount = taxableAmount × cessRate / 100. Feeds ITC cess claim in GSTR-3B 4(A). */
    @Column(name = "cess_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal cessAmt = BigDecimal.ZERO;

    /** Goods vs. Services classification. Drives HSN/SAC routing in purchase register. */
    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 10)
    private LineType lineType = LineType.GOODS;

    /** GSTN Unit Quantity Code for HSN Summary reporting. */
    @Column(name = "uqc", nullable = false, length = 10)
    private String uqc = GstnUqc.OTH.getCode();

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    public PurchaseInvoice getPurchaseInvoice() { return purchaseInvoice; }
    public void setPurchaseInvoice(PurchaseInvoice purchaseInvoice) { this.purchaseInvoice = purchaseInvoice; }

    public ItemVariant getItemVariant() { return itemVariant; }
    public void setItemVariant(ItemVariant itemVariant) { this.itemVariant = itemVariant; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }

    public BigDecimal getGstRate() { return gstRate; }
    public void setGstRate(BigDecimal gstRate) { this.gstRate = gstRate; }

    public BigDecimal getCgstAmount() { return cgstAmount; }
    public void setCgstAmount(BigDecimal cgstAmount) { this.cgstAmount = cgstAmount; }

    public BigDecimal getSgstAmount() { return sgstAmount; }
    public void setSgstAmount(BigDecimal sgstAmount) { this.sgstAmount = sgstAmount; }

    public BigDecimal getIgstAmount() { return igstAmount; }
    public void setIgstAmount(BigDecimal igstAmount) { this.igstAmount = igstAmount; }

    public BigDecimal getUtgstAmount() { return utgstAmount; }
    public void setUtgstAmount(BigDecimal utgstAmount) { this.utgstAmount = utgstAmount; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }

    public GSTType getGstTypeEnum() { return gstTypeEnum; }
    public void setGstTypeEnum(GSTType gstTypeEnum) { this.gstTypeEnum = gstTypeEnum; }

    public BigDecimal getCessRate() { return cessRate; }
    public void setCessRate(BigDecimal cessRate) {
        this.cessRate = cessRate != null ? cessRate : BigDecimal.ZERO;
    }

    public BigDecimal getCessAmt() { return cessAmt; }
    public void setCessAmt(BigDecimal cessAmt) {
        this.cessAmt = cessAmt != null ? cessAmt : BigDecimal.ZERO;
    }

    public LineType getLineType() { return lineType; }
    public void setLineType(LineType lineType) {
        this.lineType = lineType != null ? lineType : LineType.GOODS;
    }

    public String getUqc() { return uqc; }
    public void setUqc(String uqc) {
        this.uqc = (uqc != null && !uqc.isBlank()) ? uqc.trim().toUpperCase() : GstnUqc.OTH.getCode();
    }

    /** Returns the UQC for GSTN filings — "NA" for service lines. */
    public String getGstnUqc() {
        return lineType == LineType.SERVICES ? GstnUqc.gstnCodeForService() : uqc;
    }
}
