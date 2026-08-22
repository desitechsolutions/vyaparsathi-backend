package com.desitech.vyaparsathi.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalanceDto {
    private Long id;
    private Long employeeId;
    private Long leaveTypeId;
    private String leaveTypeName;
    private Integer year;
    private BigDecimal openingBalance;
    private BigDecimal allocated;
    private BigDecimal used;
    private BigDecimal carriedForward;
    private BigDecimal closingBalance;
}
