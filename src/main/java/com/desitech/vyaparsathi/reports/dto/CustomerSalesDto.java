package com.desitech.vyaparsathi.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSalesDto {
    private Long customerId;
    private String customerName;
    private BigDecimal totalSales;
    private BigDecimal totalDue;
}