package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "item_variant")
@Getter
@Setter
@NoArgsConstructor
public class ItemVariant extends ShopAwareEntity {

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "price_per_unit", nullable = false)
    private BigDecimal pricePerUnit;

    @Column
    private String hsn;

    @Column(name = "gst_rate", nullable = true)
    private Integer gstRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gst_category", nullable = false, length = 30)
    private com.desitech.vyaparsathi.gst.enums.GSTCategory gstCategory = com.desitech.vyaparsathi.gst.enums.GSTCategory.TAXABLE;

    @Column(name = "photo_path")
    private String photoPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    @JsonBackReference
    private Item item;

    @Column
    private String color;

    @Column
    private String size;

    @Column
    private String design;

    @Column
    private String fit;

    @Column(name = "low_stock_threshold")
    private BigDecimal lowStockThreshold; // Threshold for low stock alerts

    // --- Generic retail fields (batch/expiry/MRP traceability) ---

    /**
     * Batch/lot number assigned by the manufacturer.
     * Useful for traceability, recall management, and FMCG/food/retail compliance.
     */
    @Column(name = "batch_number")
    private String batchNumber;

    /**
     * Date of manufacture (printed on packaging). Optional.
     */
    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    /**
     * Expiry date of this batch. Used for expiry alerts and preventing sale of expired items.
     * Relevant for food, FMCG, cosmetics, and pharmaceutical retail.
     */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /**
     * Maximum Retail Price (MRP) — the legally printed maximum price on Indian retail packaging.
     * Selling price must not exceed MRP.
     */
    @Column(name = "mrp", precision = 12, scale = 2)
    private BigDecimal mrp;

    /**
     * Barcode (EAN-13, Code128, QR, etc.) for POS scanner lookup.
     * Added by V50 migration.
     */
    @Column(name = "barcode", length = 100)
    private String barcode;

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

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

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

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public LocalDate getManufacturingDate() { return manufacturingDate; }
    public void setManufacturingDate(LocalDate manufacturingDate) { this.manufacturingDate = manufacturingDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
}
