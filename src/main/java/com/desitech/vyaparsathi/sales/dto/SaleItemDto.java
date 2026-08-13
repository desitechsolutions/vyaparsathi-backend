package com.desitech.vyaparsathi.sales.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SaleItemDto {

    /**
     * Primary key of the persisted {@code SaleItem} row. Populated by the mapper
     * on reads; null on writes (server generates it). This is the id
     * {@code POST /api/sales/{id}/return} keys on to target a specific line —
     * required for custom (variant-less) lines that {@link #itemVariantId}
     * can't identify.
     */
    private Long saleItemId;

    private Long itemId;

    /**
     * FK to catalog ItemVariant. Nullable — when null, the line is a custom
     * (service / one-off) line and {@link #customItemName} must be provided.
     * See {@link #isEitherCatalogOrCustom()}.
     */
    @JsonProperty("id")
    private Long itemVariantId;

    @NotBlank(message = "Item name cannot be blank")
    private String itemName;
    @NotNull(message = "Quantity cannot be null")
    @Min(value = 0, message = "Quantity must be a positive number")
    private BigDecimal qty;
    @NotNull(message = "Unit price cannot be null")
    private BigDecimal unitPrice;
    private BigDecimal costPerUnit;
    private BigDecimal discount = BigDecimal.ZERO;
    private int gstRate;
    private BigDecimal taxableValue;
    private BigDecimal returnedQty;
    private BigDecimal netQty;

    /**
     * Batch/lot number of the specific item sold.
     * Captured at point-of-sale for batch traceability and recall tracking.
     */
    private String batchNumber;

    /**
     * Expiry date of the specific batch sold.
     * Stored per sale-item for invoice printing and traceability compliance.
     */
    private java.time.LocalDate expiryDate;

    /**
     * Per-line salesperson (user id). Null means the sale-level attribution on
     * {@code SaleDto.salespersonId} applies. Populated for shift reconciliation
     * when different staff dispatch different lines on the same sale.
     */
    private Long salespersonId;

    // --- Custom (free-text) line fields — used when itemVariantId is null ---
    private String customItemName;
    private String customDescription;
    private String customHsnSac;
    private String customUnit;

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public Long getItemVariantId() { return itemVariantId; }
    public void setItemVariantId(Long itemVariantId) { this.itemVariantId = itemVariantId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getCostPerUnit() { return costPerUnit; }
    public void setCostPerUnit(BigDecimal costPerUnit) { this.costPerUnit = costPerUnit; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public int getGstRate() { return gstRate; }
    public void setGstRate(int gstRate) { this.gstRate = gstRate; }

    public BigDecimal getTaxableValue() { return taxableValue; }
    public void setTaxableValue(BigDecimal taxableValue) { this.taxableValue = taxableValue; }

    public BigDecimal getReturnedQty() { return returnedQty; }
    public void setReturnedQty(BigDecimal returnedQty) { this.returnedQty = returnedQty; }

    public BigDecimal getNetQty() { return netQty; }
    public void setNetQty(BigDecimal netQty) { this.netQty = netQty; }

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

    // --- Issue 1: Variant attribute fields for rich invoice descriptions ---
    private String variantSku;
    private String variantColor;
    private String variantSize;
    private String variantDesign;
    private String variantBrand;

    public String getVariantSku() { return variantSku; }
    public void setVariantSku(String variantSku) { this.variantSku = variantSku; }

    public String getVariantColor() { return variantColor; }
    public void setVariantColor(String variantColor) { this.variantColor = variantColor; }

    public String getVariantSize() { return variantSize; }
    public void setVariantSize(String variantSize) { this.variantSize = variantSize; }

    public String getVariantDesign() { return variantDesign; }
    public void setVariantDesign(String variantDesign) { this.variantDesign = variantDesign; }

    public String getVariantBrand() { return variantBrand; }
    public void setVariantBrand(String variantBrand) { this.variantBrand = variantBrand; }

    /**
     * Cross-field constraint: a line must either reference a catalog variant OR
     * be a custom line (with a name). Enforced at the DTO level and mirrored by
     * the {@code chk_sale_item_has_product_or_custom} DB CHECK on {@code sale_item}.
     */
    @AssertTrue(message = "Line item must reference a product (itemVariantId) or provide a customItemName")
    public boolean isEitherCatalogOrCustom() {
        return itemVariantId != null
                || (customItemName != null && !customItemName.trim().isEmpty());
    }
}
