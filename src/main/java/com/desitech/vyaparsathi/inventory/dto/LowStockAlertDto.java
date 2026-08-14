package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LowStockAlertDto {
    private Long itemVariantId;
    private String itemName;
    private String sku;
    private BigDecimal currentStock;
    private BigDecimal threshold;
    private String unit;
    private String alertLevel; // LOW, CRITICAL

    private Long supplierId;
    private String supplierName;
    private BigDecimal lastPurchasePrice;
    private BigDecimal quantityOnOrder;

    // ─── Velocity-driven enterprise fields ────────────────────────────
    // Populated by StockService.getLowStockAlerts using the last 30 days
    // of DEDUCT movements. Nullable when no sales history exists so the
    // frontend can render "—" without a divide-by-zero.

    /** Average units deducted per day over the trailing 30-day window. */
    private BigDecimal avgDailySales;

    /**
     * Whole days of stock remaining at the current velocity.
     * Null when {@link #avgDailySales} is zero or missing.
     */
    private Long daysOfSupply;

    /**
     * Recommended units to reorder now. Formula covers the larger of
     * "close the threshold gap after netting on-order" and "14 days of
     * demand at current velocity, netted against on-order". Rounded up.
     */
    private BigDecimal suggestedOrderQty;

    // ─── Reorder rules (V79) ──────────────────────────────────────────
    // Optional overrides for the suggestion engine. When any of these are
    // non-null, the LowStockAlerts UI shows them in the "Reorder rules"
    // popover so the buyer knows why the suggested quantity is what it is.

    private BigDecimal reorderPoint;
    private BigDecimal reorderQty;
    private BigDecimal safetyStock;
    private BigDecimal maxStock;
    private Integer leadTimeDays;

    /**
     * Preferred supplier explicitly assigned on the variant. When set, wins
     * over the "last supplier from PO history" fallback that populates
     * {@link #supplierId}/{@link #supplierName}.
     */
    private Long preferredSupplierId;
    private String preferredSupplierName;

    // ─── Velocity trend (Tier 3) ──────────────────────────────────────
    // Compares the last 15 days vs the prior 15 days. UP means recent
    // demand is accelerating (buyer should order sooner), DOWN means
    // demand is fading (buyer can hold off). FLAT is the "no clear
    // signal" bucket — either near-zero volume or a ratio within 15 %
    // of parity. Null means we couldn't compute (no history).
    private String salesTrend; // UP | DOWN | FLAT | null

    // ─── ABC classification (Tier 3) ──────────────────────────────────
    // Pareto bucket over the last 90 days of revenue. A = top 20% of
    // variants that contribute the first ~80% of revenue; B = next 30%;
    // C = bottom 50%. Null when the variant has no recorded sales in the
    // window (usually a new SKU) — treat null as "unclassified", not "C".
    private String abcClass; // A | B | C | null

    public Long getItemVariantId() { return itemVariantId; }
    public void setItemVariantId(Long itemVariantId) { this.itemVariantId = itemVariantId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public BigDecimal getCurrentStock() { return currentStock; }
    public void setCurrentStock(BigDecimal currentStock) { this.currentStock = currentStock; }

    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getAlertLevel() { return alertLevel; }
    public void setAlertLevel(String alertLevel) { this.alertLevel = alertLevel; }

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public BigDecimal getLastPurchasePrice() { return lastPurchasePrice; }
    public void setLastPurchasePrice(BigDecimal lastPurchasePrice) { this.lastPurchasePrice = lastPurchasePrice; }

    public BigDecimal getQuantityOnOrder() { return quantityOnOrder; }
    public void setQuantityOnOrder(BigDecimal quantityOnOrder) { this.quantityOnOrder = quantityOnOrder; }

    public BigDecimal getAvgDailySales() { return avgDailySales; }
    public void setAvgDailySales(BigDecimal avgDailySales) { this.avgDailySales = avgDailySales; }

    public Long getDaysOfSupply() { return daysOfSupply; }
    public void setDaysOfSupply(Long daysOfSupply) { this.daysOfSupply = daysOfSupply; }

    public BigDecimal getSuggestedOrderQty() { return suggestedOrderQty; }
    public void setSuggestedOrderQty(BigDecimal suggestedOrderQty) { this.suggestedOrderQty = suggestedOrderQty; }

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

    public String getSalesTrend() { return salesTrend; }
    public void setSalesTrend(String salesTrend) { this.salesTrend = salesTrend; }

    public String getAbcClass() { return abcClass; }
    public void setAbcClass(String abcClass) { this.abcClass = abcClass; }
}