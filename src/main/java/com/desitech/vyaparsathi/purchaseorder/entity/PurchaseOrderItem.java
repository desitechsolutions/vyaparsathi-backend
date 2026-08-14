package com.desitech.vyaparsathi.purchaseorder.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "purchase_order_item")
public class PurchaseOrderItem extends ShopAwareEntity {
    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_variant_id", nullable = false)
    private ItemVariant itemVariant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    /**
     * Cumulative received quantity for this PO line (V81). Powers the
     * ordered-vs-received progress bar in the redesigned PO detail page
     * and the corrected on-order calc in {@code
     * PurchaseOrderItemRepository.findOnOrderQuantitiesByItemVariantIds}.
     * Defaults to zero server-side; the receiving listener increments it.
     */
    @Column(name = "received_quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    // ─── V83 line-level GST / discount / HSN ──────────────────────────
    // Mirrors SaleItem so both transactional surfaces round-trip through
    // the same accounting logic. `discount` is the flat amount subtracted
    // before tax; `discountPct` is optional metadata for the UI (the
    // authoritative value is always the flat amount).
    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "discount_pct", precision = 5, scale = 2)
    private BigDecimal discountPct;

    @Column(name = "taxable_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableValue = BigDecimal.ZERO;

    @Column(name = "gst_rate")
    private Integer gstRate;

    @Column(name = "cgst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal cgstAmt = BigDecimal.ZERO;

    @Column(name = "sgst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal sgstAmt = BigDecimal.ZERO;

    @Column(name = "igst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal igstAmt = BigDecimal.ZERO;

    @Column(name = "hsn_code", length = 20)
    private String hsnCode;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(PurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }

    public ItemVariant getItemVariant() { return itemVariant; }
    public void setItemVariant(ItemVariant itemVariant) { this.itemVariant = itemVariant; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(BigDecimal receivedQuantity) { this.receivedQuantity = receivedQuantity; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount == null ? BigDecimal.ZERO : discount; }

    public BigDecimal getDiscountPct() { return discountPct; }
    public void setDiscountPct(BigDecimal discountPct) { this.discountPct = discountPct; }

    public BigDecimal getTaxableValue() { return taxableValue; }
    public void setTaxableValue(BigDecimal taxableValue) { this.taxableValue = taxableValue == null ? BigDecimal.ZERO : taxableValue; }

    public Integer getGstRate() { return gstRate; }
    public void setGstRate(Integer gstRate) { this.gstRate = gstRate; }

    public BigDecimal getCgstAmt() { return cgstAmt; }
    public void setCgstAmt(BigDecimal cgstAmt) { this.cgstAmt = cgstAmt == null ? BigDecimal.ZERO : cgstAmt; }

    public BigDecimal getSgstAmt() { return sgstAmt; }
    public void setSgstAmt(BigDecimal sgstAmt) { this.sgstAmt = sgstAmt == null ? BigDecimal.ZERO : sgstAmt; }

    public BigDecimal getIgstAmt() { return igstAmt; }
    public void setIgstAmt(BigDecimal igstAmt) { this.igstAmt = igstAmt == null ? BigDecimal.ZERO : igstAmt; }

    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal == null ? BigDecimal.ZERO : lineTotal; }
}
