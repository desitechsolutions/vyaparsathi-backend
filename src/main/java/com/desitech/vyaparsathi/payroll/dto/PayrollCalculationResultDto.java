package com.desitech.vyaparsathi.payroll.dto;

import com.desitech.vyaparsathi.payroll.service.PayrollCalculationEngine;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollCalculationResultDto {

    private Long employeeId;
    private String employeeName;

    // Attendance
    private Integer totalDays;
    private Integer workingDays;
    private BigDecimal presentDays;
    private BigDecimal paidLeaves;
    private BigDecimal lossOfPayDays;

    // Financials
    private BigDecimal monthlyBaseSalary;
    private BigDecimal grossEarnings;
    private BigDecimal totalDeductions;
    private BigDecimal netSalary;

    // Component breakdown
    private List<PayrollCalculationEngine.ComponentAmount> earnings;
    private List<PayrollCalculationEngine.ComponentAmount> deductions;
}
