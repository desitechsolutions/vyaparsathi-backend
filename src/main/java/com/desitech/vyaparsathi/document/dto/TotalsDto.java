package com.desitech.vyaparsathi.document.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Financial totals block. Fields absent on a given doc type render as blanks. */
@Getter
@Setter
@NoArgsConstructor
public class TotalsDto {
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal totalDiscount = BigDecimal.ZERO;
    private BigDecimal totalTaxable = BigDecimal.ZERO;
    private BigDecimal cgstAmount = BigDecimal.ZERO;
    private BigDecimal sgstAmount = BigDecimal.ZERO;
    private BigDecimal igstAmount = BigDecimal.ZERO;
    private BigDecimal cessAmount = BigDecimal.ZERO;
    private BigDecimal freight = BigDecimal.ZERO;
    private BigDecimal insurance = BigDecimal.ZERO;
    private BigDecimal landingCharges = BigDecimal.ZERO;
    private BigDecimal roundOff = BigDecimal.ZERO;
    private BigDecimal grandTotal = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private BigDecimal outstandingAmount = BigDecimal.ZERO;
    private String amountInWords;

    public BigDecimal getTotalTax() {
        BigDecimal cg = cgstAmount != null ? cgstAmount : BigDecimal.ZERO;
        BigDecimal sg = sgstAmount != null ? sgstAmount : BigDecimal.ZERO;
        BigDecimal ig = igstAmount != null ? igstAmount : BigDecimal.ZERO;
        BigDecimal cs = cessAmount != null ? cessAmount : BigDecimal.ZERO;
        return cg.add(sg).add(ig).add(cs);
    }
}
