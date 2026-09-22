package com.desitech.vyaparsathi.customer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Aggregated per-customer stats consumed by the enterprise Customer detail
 * KPI strip. All numeric fields default to zero; date fields may be null.
 */
public class CustomerStatsDto {

    private Long customerId;

    // Sales
    private long totalSales;
    private BigDecimal totalSalesValue = BigDecimal.ZERO;
    private BigDecimal averageOrderValue = BigDecimal.ZERO;
    private BigDecimal outstandingReceivable = BigDecimal.ZERO;

    // Returns & credits
    private long totalCreditNotes;
    private BigDecimal totalCreditNoteValue = BigDecimal.ZERO;
    private BigDecimal appliedCreditNoteValue = BigDecimal.ZERO;
    /** V105 — unapplied credit note balance. */
    private BigDecimal creditNotesOutstanding = BigDecimal.ZERO;

    // Behaviour
    private LocalDate firstSaleDate;
    private LocalDate lastSaleDate;

    // ─── V105 Enterprise additions ──────────────────────────────────────
    /** Total payment transactions received from customer. */
    private long paymentCount;
    /** Sum of all payments received. */
    private BigDecimal totalPaymentsReceived = BigDecimal.ZERO;
    /** Unallocated advance payments. */
    private BigDecimal advanceBalance = BigDecimal.ZERO;
    /** Active quotations for this customer. */
    private long quotationCount;
    /** Active sales orders for this customer. */
    private long salesOrderCount;

    // ─── V115 aging buckets ─────────────────────────────────────────────
    /**
     * Age-of-receivable buckets summing to {@link #outstandingReceivable}.
     * Bucket definition: days since invoice date for non-cancelled sales
     * with paymentStatus PENDING or PARTIALLY_PAID.
     *
     *   agingCurrent = 0–30d overdue (or not yet overdue)
     *   aging31_60   = 31–60d overdue
     *   aging61_90   = 61–90d overdue
     *   aging90Plus  = 90d+ overdue
     */
    private BigDecimal agingCurrent = BigDecimal.ZERO;
    private BigDecimal aging31_60   = BigDecimal.ZERO;
    private BigDecimal aging61_90   = BigDecimal.ZERO;
    private BigDecimal aging90Plus  = BigDecimal.ZERO;

    public BigDecimal getAgingCurrent() { return agingCurrent; }
    public void setAgingCurrent(BigDecimal v) { this.agingCurrent = v == null ? BigDecimal.ZERO : v; }

    public BigDecimal getAging31_60() { return aging31_60; }
    public void setAging31_60(BigDecimal v) { this.aging31_60 = v == null ? BigDecimal.ZERO : v; }

    public BigDecimal getAging61_90() { return aging61_90; }
    public void setAging61_90(BigDecimal v) { this.aging61_90 = v == null ? BigDecimal.ZERO : v; }

    public BigDecimal getAging90Plus() { return aging90Plus; }
    public void setAging90Plus(BigDecimal v) { this.aging90Plus = v == null ? BigDecimal.ZERO : v; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public long getTotalSales() { return totalSales; }
    public void setTotalSales(long totalSales) { this.totalSales = totalSales; }

    /** Alias for consumers expecting invoiceCount */
    public long getInvoiceCount() { return totalSales; }
    public void setInvoiceCount(long invoiceCount) { this.totalSales = invoiceCount; }

    public BigDecimal getTotalSalesValue() { return totalSalesValue; }
    public void setTotalSalesValue(BigDecimal v) { this.totalSalesValue = v == null ? BigDecimal.ZERO : v; }

    public BigDecimal getAverageOrderValue() { return averageOrderValue; }
    public void setAverageOrderValue(BigDecimal v) { this.averageOrderValue = v == null ? BigDecimal.ZERO : v; }

    /** Alias for consumers expecting avgOrderValue */
    public BigDecimal getAvgOrderValue() { return averageOrderValue; }
    public void setAvgOrderValue(BigDecimal v) { this.averageOrderValue = v == null ? BigDecimal.ZERO : v; }

    public BigDecimal getOutstandingReceivable() { return outstandingReceivable; }
    public void setOutstandingReceivable(BigDecimal v) { this.outstandingReceivable = v == null ? BigDecimal.ZERO : v; }

    public long getTotalCreditNotes() { return totalCreditNotes; }
    public void setTotalCreditNotes(long totalCreditNotes) { this.totalCreditNotes = totalCreditNotes; }

    public BigDecimal getTotalCreditNoteValue() { return totalCreditNoteValue; }
    public void setTotalCreditNoteValue(BigDecimal v) { this.totalCreditNoteValue = v == null ? BigDecimal.ZERO : v; }

    public BigDecimal getAppliedCreditNoteValue() { return appliedCreditNoteValue; }
    public void setAppliedCreditNoteValue(BigDecimal v) { this.appliedCreditNoteValue = v == null ? BigDecimal.ZERO : v; }

    public BigDecimal getCreditNotesOutstanding() { return creditNotesOutstanding; }
    public void setCreditNotesOutstanding(BigDecimal v) { this.creditNotesOutstanding = v == null ? BigDecimal.ZERO : v; }

    public LocalDate getFirstSaleDate() { return firstSaleDate; }
    public void setFirstSaleDate(LocalDate firstSaleDate) { this.firstSaleDate = firstSaleDate; }

    public LocalDate getLastSaleDate() { return lastSaleDate; }
    public void setLastSaleDate(LocalDate lastSaleDate) { this.lastSaleDate = lastSaleDate; }

    public long getPaymentCount() { return paymentCount; }
    public void setPaymentCount(long v) { this.paymentCount = v; }

    public BigDecimal getTotalPaymentsReceived() { return totalPaymentsReceived; }
    public void setTotalPaymentsReceived(BigDecimal v) { this.totalPaymentsReceived = v == null ? BigDecimal.ZERO : v; }

    public BigDecimal getAdvanceBalance() { return advanceBalance; }
    public void setAdvanceBalance(BigDecimal v) { this.advanceBalance = v == null ? BigDecimal.ZERO : v; }

    public long getQuotationCount() { return quotationCount; }
    public void setQuotationCount(long v) { this.quotationCount = v; }

    public long getSalesOrderCount() { return salesOrderCount; }
    public void setSalesOrderCount(long v) { this.salesOrderCount = v; }
}
