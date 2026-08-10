package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ItemVariantDto {
    // Fields specific to the variant
    private Long id;
    private String sku;
    private String unit;
    private BigDecimal pricePerUnit;
    private String hsn;
    private Integer gstRate;
    private com.desitech.vyaparsathi.gst.enums.GSTCategory gstCategory = com.desitech.vyaparsathi.gst.enums.GSTCategory.TAXABLE;
    private String photoPath;
    private String color;
    private String size;
    private String design;
    private String fit;
    private BigDecimal lowStockThreshold;
    private BigDecimal currentStock;

    // "Flattened" fields from the parent Item entity
    private Long itemId;
    private String itemName;
    private String description;
    private String brand;
    private Long categoryId;
    private String categoryName;
    private String fabric;
    private String season;
    private String attribute1;
    private String attribute2;

    // --- Generic retail fields (batch/expiry/MRP traceability) ---
    /** Batch/lot number for this variant. */
    private String batchNumber;
    /** Date of manufacture printed on the packaging. */
    private LocalDate manufacturingDate;
    /** Expiry date printed on the packaging. */
    private LocalDate expiryDate;
    /** Maximum Retail Price (MRP). */
    private BigDecimal mrp;
    /** Product specifications – inherited from parent Item (renamed from composition). */
    private String specifications;
    /** Barcode value (EAN-13, Code128, QR, etc.) for POS scanner lookup. */
    private String barcode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; }

    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }

    public Integer getGstRate() { return gstRate; }
    public void setGstRate(Integer gstRate) { this.gstRate = gstRate; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getDesign() { return design; }
    public void setDesign(String design) { this.design = design; }

    public String getFit() { return fit; }
    public void setFit(String fit) { this.fit = fit; }

    public BigDecimal getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(BigDecimal lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }

    public BigDecimal getCurrentStock() { return currentStock; }
    public void setCurrentStock(BigDecimal currentStock) { this.currentStock = currentStock; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getFabric() { return fabric; }
    public void setFabric(String fabric) { this.fabric = fabric; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public String getAttribute1() { return attribute1; }
    public void setAttribute1(String attribute1) { this.attribute1 = attribute1; }

    public String getAttribute2() { return attribute2; }
    public void setAttribute2(String attribute2) { this.attribute2 = attribute2; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public LocalDate getManufacturingDate() { return manufacturingDate; }
    public void setManufacturingDate(LocalDate manufacturingDate) { this.manufacturingDate = manufacturingDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }

    public String getSpecifications() { return specifications; }
    public void setSpecifications(String specifications) { this.specifications = specifications; }

    public com.desitech.vyaparsathi.gst.enums.GSTCategory getGstCategory() { return gstCategory; }
    public void setGstCategory(com.desitech.vyaparsathi.gst.enums.GSTCategory gstCategory) { this.gstCategory = gstCategory; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
}
