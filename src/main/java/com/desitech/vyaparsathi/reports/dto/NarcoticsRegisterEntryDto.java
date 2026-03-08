package com.desitech.vyaparsathi.reports.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for a single entry in the Narcotics &amp; Controlled Drug Register.
 * Covers sales of Schedule H, H1, and X (narcotic) drugs.
 * Returned by GET /api/reports/narcotics-register?from={from}&amp;to={to}
 */
@Data
public class NarcoticsRegisterEntryDto {
    private LocalDateTime saleDate;
    private String invoiceNo;
    private String itemName;
    /** Active pharmaceutical composition (e.g., "Morphine 10mg"). */
    private String composition;
    /** Drug schedule: SCHEDULE_H, SCHEDULE_H1, SCHEDULE_X. */
    private String drugSchedule;
    private BigDecimal qty;
    private String unit;
    private String customerName;
    /** Prescribing doctor name – must be recorded for H1 and X drugs. */
    private String doctorName;
    /** Patient name when different from the buyer. */
    private String patientName;
    /** Batch number of the dispensed medicine. */
    private String batchNumber;
}
