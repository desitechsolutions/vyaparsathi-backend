package com.desitech.vyaparsathi.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockImportRowPreviewDto {
    private int rowNumber;
    private String itemName;
    private String sku;
    private String unit;
    private BigDecimal sellingPrice;
    private BigDecimal quantity;
    private String batchNumber;
    private String expiryDate;
    private boolean duplicate;
    private String duplicateReason;
    private String status; // VALID, DUPLICATE, ERROR
    private String errorMessage;
}
