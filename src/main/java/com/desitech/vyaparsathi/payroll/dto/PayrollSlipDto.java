package com.desitech.vyaparsathi.payroll.dto;

import com.desitech.vyaparsathi.payroll.enums.PayoutStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollSlipDto {
    private Long id;
    private Long payrollRunId;
    private Long employeeId;
    private String employeeName;
    private String attendanceSummary;
    private BigDecimal grossEarnings;
    private BigDecimal totalDeductions;
    private BigDecimal netSalary;
    private PayoutStatus status;
    private LocalDate createdAt;
    private LocalDate paidAt;
    private List<PayrollSlipItemDto> items;
}
