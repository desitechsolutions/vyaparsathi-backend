package com.desitech.vyaparsathi.payroll.dto;

import com.desitech.vyaparsathi.payroll.enums.PayrollRunStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRunDto {
    private Long id;
    private int month;
    private int year;
    private PayrollRunStatus status;
    private BigDecimal totalGrossEarnings;
    private BigDecimal totalNetPayable;
    private BigDecimal totalEmployerContributions;
    private LocalDate processingStartedAt;
    private LocalDate approvedAt;
    private Long approvedByUserId;
    private LocalDate disbursedAt;
    private Long disbursedByUserId;
    private LocalDate createdAt;
}
