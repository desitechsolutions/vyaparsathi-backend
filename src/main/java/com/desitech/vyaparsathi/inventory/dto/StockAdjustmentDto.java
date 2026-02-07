package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockAdjustmentDto {
    private Long itemVariantId;
    private BigDecimal adjustmentQuantity; // positive for increase, negative for decrease
    private BigDecimal costPerUnit; // optional, if not provided, use average cost
    private String reason; // required field
    private String batch; // optional
}