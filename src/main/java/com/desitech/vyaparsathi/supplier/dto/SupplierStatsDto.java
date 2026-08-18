package com.desitech.vyaparsathi.supplier.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Aggregated per-supplier stats consumed by the enterprise Supplier detail
 * page (KPI strip) and the list-level "top suppliers" analytics. All fields
 * are always non-null — zero when nothing exists.
 */
public class SupplierStatsDto {

    private Long supplierId;

    // Purchase Orders
    private long totalPurchaseOrders;
    private long openPurchaseOrders;
    private BigDecimal totalPoValue = BigDecimal.ZERO;

    // Goods Receipt Notes
    private long totalGrns;

    // Purchase Returns
    private long totalPurchaseReturns;
    private BigDecimal totalReturnValue = BigDecimal.ZERO;

    // Debit Notes
    private long totalDebitNotes;
    private BigDecimal totalDebitNoteValue = BigDecimal.ZERO;
    private BigDecimal appliedDebitNoteValue = BigDecimal.ZERO;

    // Ledger — running payable balance (positive = we owe supplier)
    private BigDecimal outstandingPayable = BigDecimal.ZERO;
    private LocalDate lastTransactionDate;

    // Convenience getters/setters
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }

    public long getTotalPurchaseOrders() { return totalPurchaseOrders; }
    public void setTotalPurchaseOrders(long v) { this.totalPurchaseOrders = v; }

    public long getOpenPurchaseOrders() { return openPurchaseOrders; }
    public void setOpenPurchaseOrders(long v) { this.openPurchaseOrders = v; }

    public BigDecimal getTotalPoValue() { return totalPoValue; }
    public void setTotalPoValue(BigDecimal v) { this.totalPoValue = v == null ? BigDecimal.ZERO : v; }

    public long getTotalGrns() { return totalGrns; }
    public void setTotalGrns(long v) { this.totalGrns = v; }

    public long getTotalPurchaseReturns() { return totalPurchaseReturns; }
    public void setTotalPurchaseReturns(long v) { this.totalPurchaseReturns = v; }

    public BigDecimal getTotalReturnValue() { return totalReturnValue; }
    public void setTotalReturnValue(BigDecimal v) { this.totalReturnValue = v == null ? BigDecimal.ZERO : v; }

    public long getTotalDebitNotes() { return totalDebitNotes; }
    public void setTotalDebitNotes(long v) { this.totalDebitNotes = v; }

    public BigDecimal getTotalDebitNoteValue() { return totalDebitNoteValue; }
    public void setTotalDebitNoteValue(BigDecimal v) { this.totalDebitNoteValue = v == null ? BigDecimal.ZERO : v; }

    public BigDecimal getAppliedDebitNoteValue() { return appliedDebitNoteValue; }
    public void setAppliedDebitNoteValue(BigDecimal v) { this.appliedDebitNoteValue = v == null ? BigDecimal.ZERO : v; }

    public BigDecimal getOutstandingPayable() { return outstandingPayable; }
    public void setOutstandingPayable(BigDecimal v) { this.outstandingPayable = v == null ? BigDecimal.ZERO : v; }

    public LocalDate getLastTransactionDate() { return lastTransactionDate; }
    public void setLastTransactionDate(LocalDate v) { this.lastTransactionDate = v; }
}
