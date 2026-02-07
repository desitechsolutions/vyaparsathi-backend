package com.desitech.vyaparsathi.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemsSoldDto {
    private Long itemId;
    private String itemName;
    private String sku;
    private Integer totalSold;
    private BigDecimal totalSales;
    private LocalDate lastSoldDate;
}