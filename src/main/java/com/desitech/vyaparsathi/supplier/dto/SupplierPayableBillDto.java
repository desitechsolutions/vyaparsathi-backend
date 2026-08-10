package com.desitech.vyaparsathi.supplier.dto;

import java.math.BigDecimal;

/**
 * Issue 4 Fix: Per-PO payable breakdown for supplier payment screen.
 * Net Payable = originalAmount - returnDeductions - cashPaid
 */
public class SupplierPayableBillDto {

    private Long poId;
    private String poNumber;
    private BigDecimal originalAmount;
    private BigDecimal returnDeductions;
    private BigDecimal cashPaid;
    private BigDecimal netPayable;
    private String paymentStatus; // "UNPAID", "PARTIALLY_PAID", "PAID"

    public Long getPoId() { return poId; }
    public void setPoId(Long poId) { this.poId = poId; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }

    public BigDecimal getReturnDeductions() { return returnDeductions; }
    public void setReturnDeductions(BigDecimal returnDeductions) { this.returnDeductions = returnDeductions; }

    public BigDecimal getCashPaid() { return cashPaid; }
    public void setCashPaid(BigDecimal cashPaid) { this.cashPaid = cashPaid; }

    public BigDecimal getNetPayable() { return netPayable; }
    public void setNetPayable(BigDecimal netPayable) { this.netPayable = netPayable; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
}
