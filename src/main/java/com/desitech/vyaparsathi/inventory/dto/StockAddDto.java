package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockAddDto {
    private Long itemVariantId;
    private BigDecimal quantity;
    private BigDecimal costPerUnit;
    private String batch;
}