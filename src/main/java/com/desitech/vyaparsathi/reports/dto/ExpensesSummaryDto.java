package com.desitech.vyaparsathi.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpensesSummaryDto {
    private BigDecimal totalExpenses;
    private BigDecimal operationalExpenses;
    private BigDecimal inventoryPurchases;
}
