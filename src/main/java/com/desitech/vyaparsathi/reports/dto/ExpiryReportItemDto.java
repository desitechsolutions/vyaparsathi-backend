package com.desitech.vyaparsathi.reports.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for a single row in the pharmacy expiry report.
 * Returned by GET /api/reports/expiry-report?days={days}
 */
@Data
public class ExpiryReportItemDto {
    private Long itemVariantId;
    private String itemName;
    /** Active pharmaceutical composition (e.g., "Paracetamol 500mg"). */
    private String composition;
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
