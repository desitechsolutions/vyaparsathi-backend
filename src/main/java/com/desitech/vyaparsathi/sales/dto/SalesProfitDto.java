package com.desitech.vyaparsathi.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesProfitDto {
    private Long saleId;
    private String invoiceNo;
    private LocalDateTime saleDate;
    private BigDecimal totalRevenue;
    private BigDecimal totalCOGS;
    private BigDecimal grossProfit;
    private BigDecimal grossMarginPercent;
    private String customerName;
}