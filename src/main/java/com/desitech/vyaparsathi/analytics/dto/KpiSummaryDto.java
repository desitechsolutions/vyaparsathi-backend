package com.desitech.vyaparsathi.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Headline metrics for a dashboard, always paired with the equally-sized
 * prior period so the UI can render arrows / trend chips without extra math.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KpiSummaryDto {
    private LocalDate from;
    private LocalDate to;
    private LocalDate previousFrom;
    private LocalDate previousTo;

    private KpiValueDto totalRevenue;
    private KpiValueDto saleCount;
    private KpiValueDto avgOrderValue;
    private KpiValueDto uniqueCustomers;
}
