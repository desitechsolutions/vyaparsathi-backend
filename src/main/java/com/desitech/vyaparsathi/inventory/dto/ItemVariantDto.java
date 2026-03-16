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

    // --- Pharmacy-specific fields ---
    /** Batch/lot number for this medicine variant. */
    private String batchNumber;
    /** Date of manufacture printed on the packaging. */
    private LocalDate manufacturingDate;
    /** Expiry date printed on the packaging. */
    private LocalDate expiryDate;
    /** Maximum Retail Price – selling price must not exceed this. */
    private BigDecimal mrp;
    /**
     * Whether this medicine can be dispensed loose (e.g. individual tablets from a strip).
     * When true, {@link #packSize} holds the number of dispensing units per stock unit.
     */
    private Boolean isLooseMedicine;
    /**
     * Number of dispensing units per stock unit (e.g. 15 tablets per strip).
     * Used with {@link #isLooseMedicine} for correct stock deduction at point of sale.
     */
    private BigDecimal packSize;
    /** Active pharmaceutical ingredient(s) and strength – inherited from parent Item. */
    private String composition;
    /**
     * Drug schedule classification – inherited from parent Item.
     * Used for sales-page prescription warnings and substitute lookups.
     */
    private com.desitech.vyaparsathi.inventory.enums.DrugSchedule drugSchedule;
    /**
     * Whether a valid prescription is required – inherited from parent Item.
     * Displayed as a warning at the point of sale.
     */
    private Boolean requiresPrescription;
}
