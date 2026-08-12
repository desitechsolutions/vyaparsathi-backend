package com.desitech.vyaparsathi.sales.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.sales.enums.GSTType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "sale_item")
public class SaleItem extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    @JsonBackReference
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_variant_id")
    private ItemVariant itemVariant;

    /**
     * Human-readable name for a custom (non-catalog) line item.
     * Required when {@link #itemVariant} is null (service billing, one-off charges).
     * A DB CHECK constraint enforces that at least one of the two is set.
     */
    @Column(name = "custom_item_name", length = 255)
    private String customItemName;

    /** Optional longer description for a custom line item. */
    @Column(name = "custom_description", length = 500)
    private String customDescription;

    /** Optional HSN (goods) or SAC (services) code for a custom line item. */
    @Column(name = "custom_hsn_sac", length = 20)
    private String customHsnSac;

    /** Optional unit label ("hour", "day", "box", "pcs") for a custom line item. */
    @Column(name = "custom_unit", length = 30)
    private String customUnit;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal qty;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "taxable_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "gst_type", nullable = false, length = 20)
    private GSTType gstType;

    @Column(name = "cgst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal cgstAmt;

    @Column(name = "sgst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal sgstAmt;

    @Column(name = "utgst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal utgstAmt = BigDecimal.ZERO;

    @Column(name = "igst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal igstAmt;

    @Column(name = "discount", precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "returned_qty", precision = 10, scale = 2)
    private BigDecimal returnedQty = BigDecimal.ZERO;

    @Column(name = "is_returned", nullable = false)
    private boolean isReturned = false;

    /**
     * Batch/lot number of the specific stock received.
     * Captured at point-of-sale for batch traceability and recall tracking.
     */
    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    /**
     * Expiry date of the specific batch dispensed.
     * Stored per sale-item for invoice printing and traceability compliance.
     */
    @Column(name = "expiry_date")
    private java.time.LocalDate expiryDate;

    /**
     * Per-line salesperson attribution. Nullable — legacy rows and lines that
     * inherit the sale-level {@code Sale.salespersonId} keep this null. When a
     * shift has multiple staff dispatching different items on the same sale
     * (rare but supported for shift reconciliation), each line can carry its
     * own attribution. Column added in V71.
     */
    @Column(name = "salesperson_id")
    private Long salespersonId;

    public Sale getSale() { return sale; }
    public void setSale(Sale sale) { this.sale = sale; }

    public ItemVariant getItemVariant() { return itemVariant; }
    public void setItemVariant(ItemVariant itemVariant) { this.itemVariant = itemVariant; }

    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public Long getSalespersonId() { return salespersonId; }
    public void setSalespersonId(Long salespersonId) { this.salespersonId = salespersonId; }

    public BigDecimal getTaxableValue() { return taxableValue; }
    public void setTaxableValue(BigDecimal taxableValue) { this.taxableValue = taxableValue; }

    public GSTType getGstType() { return gstType; }
    public void setGstType(GSTType gstType) { this.gstType = gstType; }

    public BigDecimal getCgstAmt() { return cgstAmt; }
    public void setCgstAmt(BigDecimal cgstAmt) { this.cgstAmt = cgstAmt; }

    public BigDecimal getSgstAmt() { return sgstAmt; }
    public void setSgstAmt(BigDecimal sgstAmt) { this.sgstAmt = sgstAmt; }

    public BigDecimal getUtgstAmt() { return utgstAmt; }
    public void setUtgstAmt(BigDecimal utgstAmt) { this.utgstAmt = utgstAmt; }

    public BigDecimal getIgstAmt() { return igstAmt; }
    public void setIgstAmt(BigDecimal igstAmt) { this.igstAmt = igstAmt; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public BigDecimal getReturnedQty() { return returnedQty; }
    public void setReturnedQty(BigDecimal returnedQty) { this.returnedQty = returnedQty; }

    public boolean isReturned() { return isReturned; }
    public void setReturned(boolean returned) { isReturned = returned; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public java.time.LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(java.time.LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getCustomItemName() { return customItemName; }
    public void setCustomItemName(String customItemName) { this.customItemName = customItemName; }

    public String getCustomDescription() { return customDescription; }
    public void setCustomDescription(String customDescription) { this.customDescription = customDescription; }

    public String getCustomHsnSac() { return customHsnSac; }
    public void setCustomHsnSac(String customHsnSac) { this.customHsnSac = customHsnSac; }

    public String getCustomUnit() { return customUnit; }
    public void setCustomUnit(String customUnit) { this.customUnit = customUnit; }

    /**
     * Convenience: {@code true} when this line references a catalog {@link ItemVariant},
     * {@code false} when it is a free-text (service / one-off) line.
     */
    public boolean isCatalogLine() {
        return itemVariant != null;
    }
}