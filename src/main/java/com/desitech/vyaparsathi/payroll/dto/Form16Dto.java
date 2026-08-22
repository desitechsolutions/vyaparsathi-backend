package com.desitech.vyaparsathi.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Form16Dto {
    private Long id;
    private String financialYear;
    private String panNumber;
    private String name;
    private BigDecimal totalSalary;
    private BigDecimal grossTotalIncome;
    private BigDecimal pfContribution;
    private BigDecimal tdsPayable;
    private BigDecimal taxPaidThisYear;
    private Boolean isVerified;
    private String pdfPath;
}
