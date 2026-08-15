package com.desitech.vyaparsathi.document.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** One HSN/SAC bucket in the compliance-summary table at the footer. */
@Getter
@Setter
@NoArgsConstructor
public class HsnSummaryRowDto {
    private String hsnSac;
    private BigDecimal taxableValue = BigDecimal.ZERO;
    private BigDecimal cgstAmount = BigDecimal.ZERO;
    private BigDecimal sgstAmount = BigDecimal.ZERO;
    private BigDecimal igstAmount = BigDecimal.ZERO;
    private BigDecimal cessAmount = BigDecimal.ZERO;
    private BigDecimal totalTax = BigDecimal.ZERO;
    private BigDecimal grandTotal = BigDecimal.ZERO;
}
