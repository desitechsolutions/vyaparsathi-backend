package com.desitech.vyaparsathi.reports.controller;

import com.desitech.vyaparsathi.common.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.desitech.vyaparsathi.reports.dto.*;
import com.desitech.vyaparsathi.reports.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
@Tag(name = "Financial Reports", description = "Business reporting with corrected financial logic. Net revenue = sales - returns/discounts, Net profit = net revenue - COGS - operational expenses (excludes inventory purchases).")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    @Autowired
    private ReportService service;

    @GetMapping("/daily")
    @Operation(summary = "Get daily report", 
               description = "Returns daily financial report with corrected calculations. Net Profit = Sales - COGS - Operational Expenses. Outstanding Receivables = Sales - Payments.")
    public ResponseEntity<DailyReportDto> getDailyReport(
            @Parameter(description = "Report date in YYYY-MM-DD format", example = "2024-01-15")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
                try {
                        var result = service.getDailyReport(date);
                        logger.info("Fetched daily report for date {}", date);
                        return ResponseEntity.ok(result);
                } catch (Exception e) {
                        logger.error("Error fetching daily report for date {}: {}", date, e.getMessage(), e);
                        throw new ApplicationException("Failed to fetch daily report", e);
                }
    }

    @GetMapping("/sales-summary")
    @Operation(
            summary = "Get sales summary for date range",
            description = "Returns comprehensive sales summary with COGS calculation and correct profit calculation. Net Profit excludes inventory purchases from expenses."
    )
    public ResponseEntity<SalesSummaryDto> getSalesSummary(
            @Parameter(description = "Start date in YYYY-MM-DD format", example = "2024-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String from,
            @Parameter(description = "End date in YYYY-MM-DD format", example = "2024-01-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String to) {

        LocalDate fromDate = (from == null || from.isBlank() || "undefined".equalsIgnoreCase(from))
                ? null
                : LocalDate.parse(from);

        LocalDate toDate = (to == null || to.isBlank() || "undefined".equalsIgnoreCase(to))
                ? null
                : LocalDate.parse(to);

                try {
                        var result = service.getSalesSummary(fromDate, toDate);
                        logger.info("Fetched sales summary from {} to {}", fromDate, toDate);
                        return ResponseEntity.ok(result);
                } catch (Exception e) {
                        logger.error("Error fetching sales summary from {} to {}: {}", fromDate, toDate, e.getMessage(), e);
                        throw new ApplicationException("Failed to fetch sales summary", e);
                }
    }

    @GetMapping("/gst-summary")
    @Operation(summary = "Get GST summary for date range", description = "Returns GST summary with taxable value and GST amounts breakdown")
    public ResponseEntity<GstSummaryDto> getGstSummary(
            @Parameter(description = "Start date in YYYY-MM-DD format", example = "2024-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date in YYYY-MM-DD format", example = "2024-01-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
                try {
                        var result = service.getGstSummary(from, to);
                        logger.info("Fetched GST summary from {} to {}", from, to);
                        return ResponseEntity.ok(result);
                } catch (Exception e) {
                        logger.error("Error fetching GST summary from {} to {}: {}", from, to, e.getMessage(), e);
                        throw new ApplicationException("Failed to fetch GST summary", e);
                }
    }

    @GetMapping("/gst-breakdown")
    @Operation(summary = "Get GST breakdown by rate", description = "Returns GST breakdown grouped by GST rates (0%, 5%, 12%, 18%, 28%)")
    public ResponseEntity<List<GstBreakdownDto>> getGstSummaryByRate(
            @Parameter(description = "Start date in YYYY-MM-DD format", example = "2024-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date in YYYY-MM-DD format", example = "2024-01-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
                try {
                        var result = service.getGstSummaryByRate(from, to);
                        logger.info("Fetched GST breakdown by rate from {} to {}", from, to);
                        return ResponseEntity.ok(result);
                } catch (Exception e) {
                        logger.error("Error fetching GST breakdown by rate from {} to {}: {}", from, to, e.getMessage(), e);
                        throw new ApplicationException("Failed to fetch GST breakdown by rate", e);
                }
    }

    @GetMapping("/items-sold")
    @Operation(summary = "Get all items sold", description = "Returns a list of all items sold with quantity, total sales, and last sold date.")
    public ResponseEntity<List<ItemsSoldDto>> getAllItemsSold(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String to
    ) {
        LocalDate fromDate = (from == null || from.isBlank() || "undefined".equalsIgnoreCase(from))
                ? null
                : LocalDate.parse(from);

        LocalDate toDate = (to == null || to.isBlank() || "undefined".equalsIgnoreCase(to))
                ? null
                : LocalDate.parse(to);

                try {
                        var result = service.getAllItemsSold(fromDate, toDate);
                        logger.info("Fetched all items sold from {} to {}", fromDate, toDate);
                        return ResponseEntity.ok(result);
                } catch (Exception e) {
                        logger.error("Error fetching all items sold from {} to {}: {}", fromDate, toDate, e.getMessage(), e);
                        throw new ApplicationException("Failed to fetch all items sold", e);
                }
    }

    @GetMapping("/category-sales")
    @Operation(summary = "Get sales by category", description = "Returns sales totals grouped by item category.")
    public ResponseEntity<List<CategorySalesDto>> getCategorySales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String to
    ) {
        LocalDate fromDate = (from == null || from.isBlank() || "undefined".equalsIgnoreCase(from))
                ? null
                : LocalDate.parse(from);

        LocalDate toDate = (to == null || to.isBlank() || "undefined".equalsIgnoreCase(to))
                ? null
                : LocalDate.parse(to);

                try {
                        var result = service.getCategorySales(fromDate, toDate);
                        logger.info("Fetched category sales from {} to {}", fromDate, toDate);
                        return ResponseEntity.ok(result);
                } catch (Exception e) {
                        logger.error("Error fetching category sales from {} to {}: {}", fromDate, toDate, e.getMessage(), e);
                        throw new ApplicationException("Failed to fetch category sales", e);
                }
    }

    @GetMapping("/z-report")
    @Operation(summary = "End-of-day (Z) report",
            description = "Cash-flow oriented shift close-out for the given date. " +
                    "Includes payment-method breakdown from the Payment ledger so multi-tender sales " +
                    "reconcile correctly against the drawer. Defaults to today.")
    public ResponseEntity<com.desitech.vyaparsathi.reports.dto.ZReportDto> getZReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String date
    ) {
        LocalDate d = (date == null || date.isBlank() || "undefined".equalsIgnoreCase(date))
                ? null
                : LocalDate.parse(date);
        try {
            var result = service.getZReport(d);
            logger.info("Fetched Z-report for date={}, salesCount={}, cashTotal={}",
                    result.getDate(), result.getSalesCount(), result.getCashSalesTotal());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching Z-report for date={}: {}", d, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch Z-report", e);
        }
    }

    @GetMapping("/salesperson-leaderboard")
    @Operation(summary = "Salesperson leaderboard",
            description = "Ranks users by attributed sales value in the window. Uses sale-level " +
                    "salespersonId only; sales without an assigned salesperson are skipped.")
    public ResponseEntity<List<com.desitech.vyaparsathi.reports.dto.SalespersonLeaderboardDto>> getSalespersonLeaderboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String to
    ) {
        LocalDate fromDate = (from == null || from.isBlank() || "undefined".equalsIgnoreCase(from))
                ? null
                : LocalDate.parse(from);
        LocalDate toDate = (to == null || to.isBlank() || "undefined".equalsIgnoreCase(to))
                ? null
                : LocalDate.parse(to);
        try {
            var result = service.getSalespersonLeaderboard(fromDate, toDate);
            logger.info("Fetched salesperson leaderboard from {} to {} — {} ranked", fromDate, toDate, result.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching salesperson leaderboard from {} to {}: {}", fromDate, toDate, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch salesperson leaderboard", e);
        }
    }

    @GetMapping("/customer-sales")
    @Operation(summary = "Get sales by customer", description = "Returns sales totals grouped by customer.")
    public ResponseEntity<List<CustomerSalesDto>> getCustomerSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String to
    ) {
        LocalDate fromDate = (from == null || from.isBlank() || "undefined".equalsIgnoreCase(from))
                ? null
                : LocalDate.parse(from);

        LocalDate toDate = (to == null || to.isBlank() || "undefined".equalsIgnoreCase(to))
                ? null
                : LocalDate.parse(to);

                try {
                        var result = service.getCustomerSales(fromDate, toDate);
                        logger.info("Fetched customer sales from {} to {}", fromDate, toDate);
                        return ResponseEntity.ok(result);
                } catch (Exception e) {
                        logger.error("Error fetching customer sales from {} to {}: {}", fromDate, toDate, e.getMessage(), e);
                        throw new ApplicationException("Failed to fetch customer sales", e);
                }
    }

    @GetMapping("/expenses-summary")
    @Operation(summary = "Get expenses summary", description = "Returns total expenses for a date range.")
    public ResponseEntity<ExpensesSummaryDto> getExpensesSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String to
    ) {
        LocalDate fromDate = (from == null || from.isBlank() || "undefined".equalsIgnoreCase(from))
                ? null
                : LocalDate.parse(from);

        LocalDate toDate = (to == null || to.isBlank() || "undefined".equalsIgnoreCase(to))
                ? null
                : LocalDate.parse(to);

                try {
                        var result = service.getExpensesSummary(fromDate, toDate);
                        logger.info("Fetched expenses summary from {} to {}", fromDate, toDate);
                        return ResponseEntity.ok(result);
                } catch (Exception e) {
                        logger.error("Error fetching expenses summary from {} to {}: {}", fromDate, toDate, e.getMessage(), e);
                        throw new ApplicationException("Failed to fetch expenses summary", e);
                }
    }

    @GetMapping("/payments-summary")
    @Operation(summary = "Get payments summary", description = "Returns total payments collected for a date range.")
    public ResponseEntity<PaymentsSummaryDto> getPaymentsSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String to
    ) {
        LocalDate fromDate = (from == null || from.isBlank() || "undefined".equalsIgnoreCase(from))
                ? null
                : LocalDate.parse(from);

        LocalDate toDate = (to == null || to.isBlank() || "undefined".equalsIgnoreCase(to))
                ? null
                : LocalDate.parse(to);

                try {
                        var result = service.getPaymentsSummary(fromDate, toDate);
                        logger.info("Fetched payments summary from {} to {}", fromDate, toDate);
                        return ResponseEntity.ok(result);
                } catch (Exception e) {
                        logger.error("Error fetching payments summary from {} to {}: {}", fromDate, toDate, e.getMessage(), e);
                        throw new ApplicationException("Failed to fetch payments summary", e);
                }
    }

    @GetMapping("/export-audit-pack")
    @Operation(summary = "Export CA Audit Pack", description = "Generates a ZIP file containing GST Sales, HSN Summary, and Purchase registers.")
    public ResponseEntity<byte[]> exportAuditPack(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            byte[] zipContent = service.generateAuditZip(from, to);
            String fileName = "Audit_Pack_" + from + "_to_" + to + ".zip";

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .header("Content-Type", "application/zip")
                    .body(zipContent);
        } catch (Exception e) {
            logger.error("ZIP Export failed", e);
            throw new ApplicationException("Failed to generate audit pack", e);
        }
    }

    // -------------------------------------------------------------------------
    // BATCH / EXPIRY REPORT ENDPOINTS (FMCG, food perishables)
    // -------------------------------------------------------------------------

    @GetMapping("/expiry-report")
    @Operation(
            summary = "Get item expiry report",
            description = "Returns all item variants whose expiry date falls within the next N days. " +
                    "Items that are already expired are also included. Used for perishable stock clearance."
    )
    public ResponseEntity<List<ExpiryReportItemDto>> getExpiryReport(
            @Parameter(description = "Number of days ahead to check for expiry (default 30)")
            @RequestParam(defaultValue = "30") int days) {
        try {
            List<ExpiryReportItemDto> result = service.getExpiryReport(days);
            logger.info("Fetched expiry report for next {} days, {} items found", days, result.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching expiry report: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to fetch expiry report", e);
        }
    }



    @GetMapping("/purchase-register")
    @Operation(
            summary = "Get batch-wise purchase register",
            description = "Returns a batch-level purchase register linking each received batch to its supplier. " +
                    "Required for supplier traceability and regulatory audits (recall trails)."
    )
    public ResponseEntity<List<PurchaseRegisterEntryDto>> getPurchaseRegister(
            @Parameter(description = "Start date in YYYY-MM-DD format", example = "2024-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date in YYYY-MM-DD format", example = "2024-01-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            List<PurchaseRegisterEntryDto> result = service.getPurchaseRegister(from, to);
            logger.info("Fetched purchase register from {} to {}, {} entries", from, to, result.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching purchase register from {} to {}: {}", from, to, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch purchase register", e);
        }
    }
}