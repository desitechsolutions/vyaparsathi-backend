package com.desitech.vyaparsathi.reports.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for a single row in the batch-wise Purchase Register.
 * Required for drug recall traceability and pharmacy regulatory audits.
 * Returned by GET /api/reports/purchase-register?from={from}&amp;to={to}
 */
@Data
public class PurchaseRegisterEntryDto {
    private LocalDate receivedDate;
    private String poNumber;
    private String supplierName;
    /** Drug Licence number of the supplier (if available). */
    private String supplierDlNumber;
    private String itemName;
    /** Active pharmaceutical composition. */
    private String composition;
    private String batchNumber;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
    private Integer receivedQty;
    private String unit;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
}
