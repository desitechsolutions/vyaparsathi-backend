package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.common.jpa.JsonMapConverter;
import com.desitech.vyaparsathi.gst.enums.GstnUqc;
import com.desitech.vyaparsathi.gst.enums.LineType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "item_variant", uniqueConstraints = {
    @UniqueConstraint(name = "uk_item_variant_shop_sku", columnNames = {"shop_id", "sku"})
})
@Getter
@Setter
@NoArgsConstructor
public class ItemVariant extends ShopAwareEntity {

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "price_per_unit", nullable = false)
    private BigDecimal pricePerUnit;

    @Column
    private String hsn;

    @Column(name = "gst_rate", nullable = true)
    private Integer gstRate;

    /**
     * Default GSTN Unit Quantity Code for all sale/purchase lines created from this variant.
     * Propagated to {@code SaleItem.uqc} and {@code PurchaseInvoiceItem.uqc} at line creation.
     * Must be a valid value from the {@link GstnUqc} master list, or {@code "OTH"}.
     *
     * <p>Update this field when onboarding new SKUs — a bulk SQL update by HSN chapter
     * (e.g. Chapter 61-63 apparel → NOS, Chapter 69 ceramics → NOS, Chapter 27 fuels → LTR)
     * is the recommended data-hygiene approach post-migration.
     */
    @Column(name = "uqc", nullable = false, length = 10)
    private String uqc = GstnUqc.OTH.getCode();

    /**
     * Whether this item is a physical good or a service.
     * Propagated to {@code SaleItem.lineType} at line creation.
     * Existing catalog items are GOODS by default; service-type variants
     * (labour, consultation, freight) must be explicitly set to SERVICES.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 10)
    private LineType lineType = LineType.GOODS;

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

    // ─── Enterprise reorder rules (V79) ────────────────────────────────
    // Nullable — a shop that has not configured any of these falls back to
    // the older lowStockThreshold-only behavior. See StockService for the
    // resolution order used when computing suggested quantities.

    /** Stock level at which to trigger a reorder. Falls back to lowStockThreshold when null. */
    @Column(name = "reorder_point", precision = 12, scale = 2)
    private BigDecimal reorderPoint;

    /** Fixed reorder quantity. When set, the suggestion engine returns this instead of a formula. */
    @Column(name = "reorder_qty", precision = 12, scale = 2)
    private BigDecimal reorderQty;

    /** Buffer stock kept for demand uncertainty; added on top of lead-time cover. */
    @Column(name = "safety_stock", precision = 12, scale = 2)
    private BigDecimal safetyStock;

    /** Maximum on-hand quantity. Advisory today; enforceable at PO creation later. */
    @Column(name = "max_stock", precision = 12, scale = 2)
    private BigDecimal maxStock;

    /** Days between placing a PO and receiving the goods. Feeds velocity × lead_time. */
    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    // ─── Explicit supplier assignment (V79) ────────────────────────────
    // Replaces the "infer from most recent PO" fallback for the preferred
    // supplier. LastSupplierInfo is still consulted when both fields are
    // null so the LowStockAlerts screen keeps working without config.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_supplier_id")
    private com.desitech.vyaparsathi.supplier.entity.Supplier preferredSupplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "backup_supplier_id")
    private com.desitech.vyaparsathi.supplier.entity.Supplier backupSupplier;

    /**
     * Last time this variant appeared in a low-stock email digest (V80).
     * The scheduler uses this as a dedupe stamp so a shop owner does not
     * get the same variant re-emailed every day for a week — the resend
     * window (default 24 h) is defined in the scheduler.
     */
    @Column(name = "alert_last_notified_at")
    private java.time.LocalDateTime alertLastNotifiedAt;

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
     * Relevant for food, FMCG, and cosmetics retail.
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

    // ─── Industry-specific columns (added by V76) ─────────────────────
    // All nullable; only the industry that needs them populates them.
    // Absent from the DB before V76.

    // Jewellery
    @Column(name = "metal_type", length = 40)
    private String metalType;

    @Column(name = "metal_purity", length = 20)
    private String metalPurity;

    @Column(name = "weight_grams", precision = 10, scale = 3)
    private BigDecimal weightGrams;

    @Column(name = "net_weight_grams", precision = 10, scale = 3)
    private BigDecimal netWeightGrams;

    @Column(name = "stone_weight_carats", precision = 10, scale = 3)
    private BigDecimal stoneWeightCarats;

    @Column(name = "hallmark_no", length = 60)
    private String hallmarkNo;

    @Column(name = "making_charges_per_gram", precision = 10, scale = 2)
    private BigDecimal makingChargesPerGram;

    @Column(name = "making_charges_pct", precision = 5, scale = 2)
    private BigDecimal makingChargesPct;

    // Electronics
    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "serial_number", length = 80)
    private String serialNumber;

    // Automobile
    @Column(name = "part_number", length = 80)
    private String partNumber;

    @Column(name = "vehicle_compatibility", length = 500)
    private String vehicleCompatibility;

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

    // Industry-specific getters/setters (V76 additions)

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

    // Enterprise reorder rules (V79) — explicit accessors for grep parity.
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

    public com.desitech.vyaparsathi.supplier.entity.Supplier getPreferredSupplier() { return preferredSupplier; }
    public void setPreferredSupplier(com.desitech.vyaparsathi.supplier.entity.Supplier preferredSupplier) { this.preferredSupplier = preferredSupplier; }

    public com.desitech.vyaparsathi.supplier.entity.Supplier getBackupSupplier() { return backupSupplier; }
    public void setBackupSupplier(com.desitech.vyaparsathi.supplier.entity.Supplier backupSupplier) { this.backupSupplier = backupSupplier; }

    public java.time.LocalDateTime getAlertLastNotifiedAt() { return alertLastNotifiedAt; }
    public void setAlertLastNotifiedAt(java.time.LocalDateTime alertLastNotifiedAt) { this.alertLastNotifiedAt = alertLastNotifiedAt; }

    // gstCategory getter/setter (the one the mapper was missing — Lombok
    // already generates these via @Getter/@Setter, but declaring explicit
    // signatures here matches the pattern used by every other field on
    // this entity so grep-based tooling can find them).
    public com.desitech.vyaparsathi.gst.enums.GSTCategory getGstCategory() { return gstCategory; }
    public void setGstCategory(com.desitech.vyaparsathi.gst.enums.GSTCategory gstCategory) { this.gstCategory = gstCategory; }

    public String getUqc() { return uqc; }
    public void setUqc(String uqc) {
        this.uqc = (uqc != null && !uqc.isBlank()) ? uqc.trim().toUpperCase() : GstnUqc.OTH.getCode();
    }

    public LineType getLineType() { return lineType; }
    public void setLineType(LineType lineType) {
        this.lineType = lineType != null ? lineType : LineType.GOODS;
    }

    /**
     * Soft-delete flag. Set to false via {@code ItemService.deleteItemVariant}
     * instead of a physical delete, so historical {@code sale_item} rows
     * continue to resolve their {@code item_variant_id} FK. Catalog queries
     * filter {@code active = true}; sales-history joins do not.
     */
    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public boolean isActive() { return active == null || active; }

    /**
     * Per-shop custom attributes as a JSON map keyed by
     * {@code ShopCustomAttributeDef.keyName}. The definitions live in
     * a separate table so a shop owner can add fields without a
     * schema migration; the values live here.
     */
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "custom_attributes", columnDefinition = "JSON")
    private Map<String, Object> customAttributes;

    public Map<String, Object> getCustomAttributes() { return customAttributes; }
    public void setCustomAttributes(Map<String, Object> customAttributes) { this.customAttributes = customAttributes; }
}
