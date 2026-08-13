package com.desitech.vyaparsathi.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for a single row in the batch/expiry report.
 * Returned by GET /api/reports/expiry-report?days={days}
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExpiryReportItemDto {
    private Long itemVariantId;
    private String itemName;
    /** Product specifications or key attributes. */
    private String specifications;
    private String sku;
    private String batchNumber;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
    /** Remaining days to expiry; negative means already expired. */
    private long daysToExpiry;
    private BigDecimal quantity;
    private String unit;
    /** EXPIRED, CRITICAL (<=30 days), WARNING (>30 days within window). */
    private String alertLevel;
}
