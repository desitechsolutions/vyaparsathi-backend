package com.desitech.vyaparsathi.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One month's worth of seasonality data. Replaces the earlier stringly-typed
 * DTO ("Sales: 12") so charting libraries can plot revenue/count directly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeasonalTrendDto {
    private int month;             // 1..12
    private String monthName;      // "Jan", "Feb", ...
    private String season;         // Winter / Spring / Summer / Autumn
    private long salesCount;
    private BigDecimal revenue;
}
