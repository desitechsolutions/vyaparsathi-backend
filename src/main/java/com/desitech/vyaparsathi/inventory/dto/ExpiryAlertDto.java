package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for medicine/product expiry alerts in pharmacy management.
 */
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
    /** EXPIRED, CRITICAL (<=30 days), WARNING (<=90 days) */
    private String alertLevel;
}
