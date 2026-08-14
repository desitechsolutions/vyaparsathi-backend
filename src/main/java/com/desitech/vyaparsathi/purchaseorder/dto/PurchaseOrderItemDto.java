package com.desitech.vyaparsathi.purchaseorder.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PurchaseOrderItemDto {
    private Long id;
    private Long itemVariantId;
    private Integer quantity;
    private BigDecimal unitCost;
    private String sku;
    private String name;
    /** V81 — cumulative received qty; used by the FE receipt-progress bar. */
    private BigDecimal receivedQuantity;

    // ─── V83 line-level GST / discount / HSN ──────────────────────────
    // Discount is the flat amount subtracted from qty × unit_cost before
    // tax. discountPct is optional UI metadata. gstRate is nullable —
    // service falls back to variant.gstRate when caller omits it. HSN
    // falls back to the parent item's HSN.
    private BigDecimal discount;
    private BigDecimal discountPct;
    private BigDecimal taxableValue;
    private Integer gstRate;
    private BigDecimal cgstAmt;
    private BigDecimal sgstAmt;
    private BigDecimal igstAmt;
    private String hsnCode;
    private BigDecimal lineTotal;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getItemVariantId() { return itemVariantId; }
    public void setItemVariantId(Long itemVariantId) { this.itemVariantId = itemVariantId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(BigDecimal receivedQuantity) { this.receivedQuantity = receivedQuantity; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public BigDecimal getDiscountPct() { return discountPct; }
    public void setDiscountPct(BigDecimal discountPct) { this.discountPct = discountPct; }

    public BigDecimal getTaxableValue() { return taxableValue; }
    public void setTaxableValue(BigDecimal taxableValue) { this.taxableValue = taxableValue; }

    public Integer getGstRate() { return gstRate; }
    public void setGstRate(Integer gstRate) { this.gstRate = gstRate; }

    public BigDecimal getCgstAmt() { return cgstAmt; }
    public void setCgstAmt(BigDecimal cgstAmt) { this.cgstAmt = cgstAmt; }

    public BigDecimal getSgstAmt() { return sgstAmt; }
    public void setSgstAmt(BigDecimal sgstAmt) { this.sgstAmt = sgstAmt; }

    public BigDecimal getIgstAmt() { return igstAmt; }
    public void setIgstAmt(BigDecimal igstAmt) { this.igstAmt = igstAmt; }

    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
