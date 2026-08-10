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

    public Long getStaffId() { return staffId; }
    public void setStaffId(Long staffId) { this.staffId = staffId; }

    public String getSalaryMonth() { return salaryMonth; }
    public void setSalaryMonth(String salaryMonth) { this.salaryMonth = salaryMonth; }

    public Integer getSalaryYear() { return salaryYear; }
    public void setSalaryYear(Integer salaryYear) { this.salaryYear = salaryYear; }

    public BigDecimal getBonus() { return bonus; }
    public void setBonus(BigDecimal bonus) { this.bonus = bonus; }

    public BigDecimal getDeductions() { return deductions; }
    public void setDeductions(BigDecimal deductions) { this.deductions = deductions; }

    public BigDecimal getAdvanceDeduction() { return advanceDeduction; }
    public void setAdvanceDeduction(BigDecimal advanceDeduction) { this.advanceDeduction = advanceDeduction; }

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}