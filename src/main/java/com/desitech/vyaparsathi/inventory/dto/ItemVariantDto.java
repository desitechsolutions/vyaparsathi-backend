package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;
import java.math.BigDecimal;

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
    private BigDecimal lowStockThreshold; // Changed to Integer for consistency with entity
    private BigDecimal currentStock;

    // "Flattened" fields from the parent Item entity
    private Long itemId;
    private String itemName;
    private String description;
    private String brand;
    private Long categoryId; // Corrected from 'String category'
    private String categoryName; // Added for completeness
    private String fabric; // Added from parent
    private String season; // Added
}