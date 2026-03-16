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

    // --- Pharmacy-specific fields ---
    /** Batch/lot number assigned by the manufacturer. */
    private String batchNumber;
    /** Expiry date of this variant/batch (used for expiry display in stock overview). */
    private LocalDate expiryDate;
    /** Maximum Retail Price – selling price must not exceed this. */
    private BigDecimal mrp;
    /** Whether this medicine can be dispensed in sub-units (e.g. individual tablets). */
    private Boolean isLooseMedicine;
    /** Number of dispensing units per stock unit (e.g. 15 tablets per strip). */
    private BigDecimal packSize;
}
