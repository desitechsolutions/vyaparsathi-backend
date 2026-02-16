package com.desitech.vyaparsathi.payroll.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PayrollRequestDto {

    @NotNull(message = "Staff ID is required")
    private Long staffId;

    @NotBlank(message = "Salary month is required")
    private String salaryMonth; // e.g., "January"

    @NotNull(message = "Salary year is required")
    @Min(2024)
    private Integer salaryYear;

    @DecimalMin(value = "0.0", message = "Bonus cannot be negative")
    private BigDecimal bonus = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "Deductions cannot be negative")
    private BigDecimal deductions = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "Advance deduction cannot be negative")
    private BigDecimal advanceDeduction = BigDecimal.ZERO;

    @NotBlank(message = "Payment mode is required")
    private String paymentMode; // CASH, UPI, BANK

    private String remarks;
}