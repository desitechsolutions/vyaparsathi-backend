package com.desitech.vyaparsathi.sales.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SaleItemDto {
    private Long itemId;
    @JsonProperty("id")
    @NotNull(message = "Item Variant ID cannot be null")
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
     * Batch/lot number of the specific item dispensed.
     * Captured at point-of-sale for batch traceability and recall tracking.
     */
    private String batchNumber;

    /**
     * Expiry date of the specific batch dispensed.
     * Stored per sale-item for invoice printing and traceability compliance.
     */
    private java.time.LocalDate expiryDate;

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
}