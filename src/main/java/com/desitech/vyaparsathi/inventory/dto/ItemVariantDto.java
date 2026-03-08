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
    /** Active pharmaceutical ingredient(s) and strength – inherited from parent Item. */
    private String composition;
}
