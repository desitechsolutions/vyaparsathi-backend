package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class StockAddDto {
    private Long itemVariantId;
    private BigDecimal quantity;
    private BigDecimal costPerUnit;
    private String batch;
    private LocalDate expiryDate;
}
