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
}