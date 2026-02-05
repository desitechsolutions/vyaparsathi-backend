package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

import lombok.Data;
import java.math.BigDecimal;

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
}
