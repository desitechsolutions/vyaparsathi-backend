package com.desitech.vyaparsathi.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaxDeclarationDto {
    private Long id;
    private String financialYear;
    private String taxRegime;
    private BigDecimal lifeInsurancePremium;
    private BigDecimal medicalInsurancePremium;
    private BigDecimal educationExpenses;
    private BigDecimal npsContribution;
    private BigDecimal totalTaxableIncome;
}
