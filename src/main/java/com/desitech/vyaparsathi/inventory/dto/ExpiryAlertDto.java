package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpiryAlertDto {
    private Long itemVariantId;
    private String itemName;
    private String sku;
    private String batchNumber;
    private LocalDate expiryDate;
    private long daysToExpiry;
    private BigDecimal currentStock;
    private String unit;
    private String alertLevel;

    public Long getItemVariantId() { return itemVariantId; }
    public void setItemVariantId(Long itemVariantId) { this.itemVariantId = itemVariantId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public long getDaysToExpiry() { return daysToExpiry; }
    public void setDaysToExpiry(long daysToExpiry) { this.daysToExpiry = daysToExpiry; }

    public BigDecimal getCurrentStock() { return currentStock; }
    public void setCurrentStock(BigDecimal currentStock) { this.currentStock = currentStock; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getAlertLevel() { return alertLevel; }
    public void setAlertLevel(String alertLevel) { this.alertLevel = alertLevel; }
}
