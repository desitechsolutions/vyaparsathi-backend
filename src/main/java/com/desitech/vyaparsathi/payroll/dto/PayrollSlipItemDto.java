package com.desitech.vyaparsathi.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollSlipItemDto {
    private Long id;
    private Long payrollSlipId;
    private String componentCode;
    private String componentName;
    private BigDecimal amount;
    private Integer sequence;
}
