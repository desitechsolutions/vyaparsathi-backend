package com.desitech.vyaparsathi.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * End-of-day (Z-report) shift close-out — the sheet a cashier reconciles the
 * drawer against at close. Focused on cash-flow, not P&L; see {@link DailyReportDto}
 * for margins / COGS / receivables.
 *
 * Cash-drawer opening/closing balances are computed client-side against user
 * input in this first pass — no persisted drawer session yet. When drawer
 * sessions ship, extend this DTO with sessionId + openingBalance persisted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZReportDto {
    private LocalDate date;

    /** Count of non-cancelled Sale rows dated within the day. */
    private long salesCount;
    /** Count of CANCELLED sales dated within the day. */
    private long cancelledCount;
    /** Count of sales with any returned quantity (PARTIALLY_RETURNED or RETURNED). */
    private long returnedCount;

    /** Sum of {@code Sale.grandTotal} for non-cancelled sales. */
    private BigDecimal grossSales;
    /** Sum of line + bill-level discounts applied to non-cancelled sales. */
    private BigDecimal totalDiscount;
    /** Sum of GST (CGST+SGST+IGST+CESS) on non-cancelled sales. */
    private BigDecimal totalGst;
    /** {@code grossSales} - refund amount on today's returns. */
    private BigDecimal netSales;

    /** Payment volume grouped by method — the drawer-reconciliation heart of the report. */
    private List<PaymentMethodBreakdown> paymentBreakdown;
    /** Sum of {@code paymentBreakdown} where method = CASH. Convenience for drawer count. */
    private BigDecimal cashSalesTotal;
    /** Sum of {@code paymentBreakdown} for all non-cash methods. */
    private BigDecimal digitalSalesTotal;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodBreakdown {
        private String method;   // CASH / CARD / UPI / NET_BANKING / CHEQUE / OTHER
        private BigDecimal amount;
        private long txnCount;
    }
}
