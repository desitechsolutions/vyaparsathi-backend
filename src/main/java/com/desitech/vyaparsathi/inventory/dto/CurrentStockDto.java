package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CurrentStockDto {
    private Long itemVariantId;
    private String itemName;      // Name of the parent item
    private String sku;
    private String unit;
    private String color;
    private String size;
    private String design;
    private BigDecimal pricePerUnit;
    private BigDecimal costPerUnit;
    private BigDecimal totalQuantity;
    private String batch;         // Optional, can be null if not required

    // --- Generic retail fields ---
    /** Batch/lot number assigned by the manufacturer. */
    private String batchNumber;
    /** Expiry date of this variant/batch (used for expiry display in stock overview). */
    private LocalDate expiryDate;
    /** Maximum Retail Price – selling price must not exceed this. */
    private BigDecimal mrp;

    // --- Fields added by the enterprise Stock-page redesign ---
    /** Fourth free-form variant attribute (e.g., fit / connectivity / position). */
    private String fit;
    /** Parent item's brand name — surfaced for the "Category · Brand" column and brand filter. */
    private String brandName;
    /** Parent item's category name — powers the category filter. */
    private String categoryName;
    /** Per-variant low-stock threshold; drives the stock-level dot correctly (was 10 hardcoded). */
    private BigDecimal lowStockThreshold;
    /** Variant thumbnail — same source of truth as ProductDto.photoPath. */
    private String photoPath;
    /** HSN code for GST filing. */
    private String hsn;
    /** GST rate percentage. */
    private Integer gstRate;

    public Long getItemVariantId() { return itemVariantId; }
    public void setItemVariantId(Long itemVariantId) { this.itemVariantId = itemVariantId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getDesign() { return design; }
    public void setDesign(String design) { this.design = design; }

    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; }

    public BigDecimal getCostPerUnit() { return costPerUnit; }
    public void setCostPerUnit(BigDecimal costPerUnit) { this.costPerUnit = costPerUnit; }

    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }

    public String getFit() { return fit; }
    public void setFit(String fit) { this.fit = fit; }

    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public BigDecimal getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(BigDecimal lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }

    public Integer getGstRate() { return gstRate; }
    public void setGstRate(Integer gstRate) { this.gstRate = gstRate; }
}
