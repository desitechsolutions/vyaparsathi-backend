package com.desitech.vyaparsathi.payroll.dto;

import com.desitech.vyaparsathi.payroll.enums.PayrollStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PayrollResponseDto {
    private Long id;
    private Long staffId;
    private String staffName;
    private String staffRole;

    private String salaryMonth;
    private Integer salaryYear;
    private LocalDate paymentDate;

    private BigDecimal baseSalaryAtTime;
    private BigDecimal bonus;
    private BigDecimal deductions;
    private BigDecimal advanceDeduction;
    private BigDecimal netAmount;

    private PayrollStatus status; // Returns the Enum value (PAID, PENDING, etc.)
    private String paymentMode;
    private String remarks;
}