package com.desitech.vyaparsathi.inventory.dto;

import com.desitech.vyaparsathi.gst.enums.GSTCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
public class ItemVariantDto {
    // ── Core variant identity ─────────────────────────────────────────
    private Long id;

    /**
     * SKU. Optional on the wire — {@link
     * com.desitech.vyaparsathi.inventory.service.ItemService#assignHsnAndSkuCodes}
     * auto-generates one from the item's category + brand + variant axes
     * when the client submits it blank. The DB column stays {@code NOT
     * NULL UNIQUE}; the service is the one making sure that contract is
     * met. Do NOT put {@code @NotBlank} back on this field — it would
     * short-circuit Spring bean validation before the auto-gen ever runs.
     */
    @Size(max = 100)
    private String sku;

    @NotBlank(message = "Unit is required")
    @Size(max = 20)
    private String unit;

    @NotNull(message = "Price per unit is required")
    @Positive(message = "Price per unit must be greater than zero")
    private BigDecimal pricePerUnit;

    private String hsn;

    @Min(value = 0, message = "GST rate cannot be negative")
    @Max(value = 28, message = "GST rate must be 28% or lower")
    private Integer gstRate;

    /** Non-null; defaults to TAXABLE if omitted by the client. */
    private GSTCategory gstCategory = GSTCategory.TAXABLE;

    private String photoPath;

    // ── Variant axes (relabelled per industry on the frontend) ────────
    private String color;
    private String size;
    private String design;
    private String fit;

    @PositiveOrZero(message = "Low-stock threshold cannot be negative")
    private BigDecimal lowStockThreshold;

    /** Live stock — enriched by StockService.enrichCurrentStock, not by the mapper. */
    private BigDecimal currentStock;

    // ── Flattened parent-item fields (read-only on write, ignored) ────
    private Long itemId;
    private String itemName;
    private String description;
    private String brand;
    private Long categoryId;
    private String categoryName;

    /** Generic attribute slot #1 — inherited from parent Item. */
    private String attribute1;

    /** Generic attribute slot #2 — inherited from parent Item. */
    private String attribute2;

    /** Product specifications — inherited from parent Item (renamed from composition). */
    private String specifications;

    // ── Generic retail traceability (batch / expiry / MRP / barcode) ──
    /** Batch/lot number for this variant. */
    @Size(max = 50)
    private String batchNumber;

    /** Date of manufacture printed on the packaging. */
    private LocalDate manufacturingDate;

    /** Expiry date printed on the packaging. */
    private LocalDate expiryDate;

    /** Maximum Retail Price (MRP). Must be at least the selling price. */
    @DecimalMin(value = "0.00", message = "MRP cannot be negative")
    private BigDecimal mrp;

    /** Barcode value (EAN-13, Code128, QR, etc.) for POS scanner lookup. */
    @Size(max = 100)
    private String barcode;

    // ── Industry-specific fields (V76) — nullable, populated per industry ──

    // Jewellery
    @Size(max = 40)  private String metalType;
    @Size(max = 20)  private String metalPurity;
    @DecimalMin(value = "0.000") private BigDecimal weightGrams;
    @DecimalMin(value = "0.000") private BigDecimal netWeightGrams;
    @DecimalMin(value = "0.000") private BigDecimal stoneWeightCarats;
    @Size(max = 60)  private String hallmarkNo;
    @DecimalMin(value = "0.00")  private BigDecimal makingChargesPerGram;
    @DecimalMin(value = "0.00")  private BigDecimal makingChargesPct;

    // Electronics
    @Min(value = 0, message = "Warranty months cannot be negative")
    private Integer warrantyMonths;

    @Size(max = 80)
    private String serialNumber;

    // Automobile
    @Size(max = 80)
    private String partNumber;

    @Size(max = 500)
    private String vehicleCompatibility;

    // ── Getters / setters (Lombok @Data covers these, but explicit
    //    signatures kept for the existing style in this codebase) ─────

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

    public GSTCategory getGstCategory() { return gstCategory; }
    public void setGstCategory(GSTCategory gstCategory) { this.gstCategory = gstCategory; }

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

    public String getAttribute1() { return attribute1; }
    public void setAttribute1(String attribute1) { this.attribute1 = attribute1; }

    public String getAttribute2() { return attribute2; }
    public void setAttribute2(String attribute2) { this.attribute2 = attribute2; }

    public String getSpecifications() { return specifications; }
    public void setSpecifications(String specifications) { this.specifications = specifications; }

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

    // Industry-specific getters/setters

    public String getMetalType() { return metalType; }
    public void setMetalType(String metalType) { this.metalType = metalType; }

    public String getMetalPurity() { return metalPurity; }
    public void setMetalPurity(String metalPurity) { this.metalPurity = metalPurity; }

    public BigDecimal getWeightGrams() { return weightGrams; }
    public void setWeightGrams(BigDecimal weightGrams) { this.weightGrams = weightGrams; }

    public BigDecimal getNetWeightGrams() { return netWeightGrams; }
    public void setNetWeightGrams(BigDecimal netWeightGrams) { this.netWeightGrams = netWeightGrams; }

    public BigDecimal getStoneWeightCarats() { return stoneWeightCarats; }
    public void setStoneWeightCarats(BigDecimal stoneWeightCarats) { this.stoneWeightCarats = stoneWeightCarats; }

    public String getHallmarkNo() { return hallmarkNo; }
    public void setHallmarkNo(String hallmarkNo) { this.hallmarkNo = hallmarkNo; }

    public BigDecimal getMakingChargesPerGram() { return makingChargesPerGram; }
    public void setMakingChargesPerGram(BigDecimal makingChargesPerGram) { this.makingChargesPerGram = makingChargesPerGram; }

    public BigDecimal getMakingChargesPct() { return makingChargesPct; }
    public void setMakingChargesPct(BigDecimal makingChargesPct) { this.makingChargesPct = makingChargesPct; }

    public Integer getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getPartNumber() { return partNumber; }
    public void setPartNumber(String partNumber) { this.partNumber = partNumber; }

    public String getVehicleCompatibility() { return vehicleCompatibility; }
    public void setVehicleCompatibility(String vehicleCompatibility) { this.vehicleCompatibility = vehicleCompatibility; }

    /** Per-shop custom attribute values keyed by ShopCustomAttributeDef.keyName. */
    private Map<String, Object> customAttributes;
    public Map<String, Object> getCustomAttributes() { return customAttributes; }
    public void setCustomAttributes(Map<String, Object> customAttributes) { this.customAttributes = customAttributes; }

    // ─── Enterprise reorder rules (V79) ────────────────────────────────
    // Optional. See ItemVariant entity for full semantics.

    @DecimalMin(value = "0.00", message = "Reorder point cannot be negative")
    private BigDecimal reorderPoint;

    @DecimalMin(value = "0.00", message = "Reorder qty cannot be negative")
    private BigDecimal reorderQty;

    @DecimalMin(value = "0.00", message = "Safety stock cannot be negative")
    private BigDecimal safetyStock;

    @DecimalMin(value = "0.00", message = "Max stock cannot be negative")
    private BigDecimal maxStock;

    @Min(value = 0, message = "Lead time cannot be negative")
    private Integer leadTimeDays;

    /** IDs are the FK the FE ships; names are read-only, filled by the mapper. */
    private Long preferredSupplierId;
    private String preferredSupplierName;

    private Long backupSupplierId;
    private String backupSupplierName;

    public BigDecimal getReorderPoint() { return reorderPoint; }
    public void setReorderPoint(BigDecimal reorderPoint) { this.reorderPoint = reorderPoint; }

    public BigDecimal getReorderQty() { return reorderQty; }
    public void setReorderQty(BigDecimal reorderQty) { this.reorderQty = reorderQty; }

    public BigDecimal getSafetyStock() { return safetyStock; }
    public void setSafetyStock(BigDecimal safetyStock) { this.safetyStock = safetyStock; }

    public BigDecimal getMaxStock() { return maxStock; }
    public void setMaxStock(BigDecimal maxStock) { this.maxStock = maxStock; }

    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; }

    public Long getPreferredSupplierId() { return preferredSupplierId; }
    public void setPreferredSupplierId(Long preferredSupplierId) { this.preferredSupplierId = preferredSupplierId; }

    public String getPreferredSupplierName() { return preferredSupplierName; }
    public void setPreferredSupplierName(String preferredSupplierName) { this.preferredSupplierName = preferredSupplierName; }

    public Long getBackupSupplierId() { return backupSupplierId; }
    public void setBackupSupplierId(Long backupSupplierId) { this.backupSupplierId = backupSupplierId; }

    public String getBackupSupplierName() { return backupSupplierName; }
    public void setBackupSupplierName(String backupSupplierName) { this.backupSupplierName = backupSupplierName; }
}
