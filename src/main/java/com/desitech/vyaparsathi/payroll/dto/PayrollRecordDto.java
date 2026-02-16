package com.desitech.vyaparsathi.payroll.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PayrollRecordDto {
    private Long id;
    private Long staffId;
    private String staffName;
    private LocalDate paymentDate;
    private BigDecimal baseSalary;
    private BigDecimal bonus;
    private BigDecimal deductions;
    private BigDecimal netAmount;
    private String status;
}