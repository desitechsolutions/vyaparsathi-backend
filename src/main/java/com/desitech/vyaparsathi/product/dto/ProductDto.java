package com.desitech.vyaparsathi.product.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Variant-level projection served by {@code GET /api/products} for the
 * Products browse page. Every row is one SKU; parent-item fields
 * (name, brand, category) are flattened onto the DTO so the UI can
 * render without joining client-side.
 *
 * <p>Enterprise redesign added: mrp, hsn, gstRate, unit, categoryName,
 * brandName, lowStockThreshold — all present on the underlying entity
 * but previously discarded by the service layer.
 */
@Data
public class ProductDto {
    private Long itemVariantId;
    private String itemName;
    private String description;
    private String sku;
    private String color;
    private String size;
    private String design;
    private String fit;
    private String unit;
    private BigDecimal pricePerUnit;
    private BigDecimal mrp;
    private String hsn;
    private Integer gstRate;
    private BigDecimal availableQuantity;
    private BigDecimal lowStockThreshold;
    private String batch;
    private String photoPath;
    private String categoryName;
    private String brandName;

    public Long getItemVariantId() { return itemVariantId; }
    public void setItemVariantId(Long itemVariantId) { this.itemVariantId = itemVariantId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getDesign() { return design; }
    public void setDesign(String design) { this.design = design; }

    public String getFit() { return fit; }
    public void setFit(String fit) { this.fit = fit; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; }

    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }

    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }

    public Integer getGstRate() { return gstRate; }
    public void setGstRate(Integer gstRate) { this.gstRate = gstRate; }

    public BigDecimal getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(BigDecimal availableQuantity) { this.availableQuantity = availableQuantity; }

    public BigDecimal getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(BigDecimal lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
}
