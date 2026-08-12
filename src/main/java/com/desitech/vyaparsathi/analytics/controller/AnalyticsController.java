package com.desitech.vyaparsathi.analytics.controller;

import com.desitech.vyaparsathi.analytics.dto.*;
import com.desitech.vyaparsathi.analytics.model.AnalyticsRange;
import com.desitech.vyaparsathi.analytics.service.AnalyticsExportService;
import com.desitech.vyaparsathi.analytics.service.AnalyticsService;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controller for Business Intelligence and Predictive Analytics.
 * Handles demand forecasting, churn prediction, and financial procurement planning.
 */
@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
@Tag(name = "Business Analytics", description = "Predictive insights and financial procurement reports.")
public class AnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private AnalyticsExportService analyticsExportService;

    /**
     * Exports the Procurement Plan (Purchase Suggestions + Costs).
     * Replaces the old exportItemDemand to provide financial value to the user.
     */
    @GetMapping("/export/procurement-plan")
    @Operation(summary = "Export procurement plan", description = "Download purchase suggestions with estimated investment costs.")
    public ResponseEntity<Resource> exportProcurementPlan(
            @RequestParam(defaultValue = "xlsx") String format
    ) {
        try {
            logger.info("Initiating procurement plan export in format: {}", format);
            var data = analyticsService.suggestFuturePurchaseOrders();
            byte[] file = analyticsExportService.exportPurchaseSuggestions(data, format);

            String filename = "procurement_plan_" + java.time.LocalDate.now() + "." + format;
            String contentType = getContentType(format);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new ByteArrayResource(file));
        } catch (ExportAppException e) {
            logger.error("Export specific error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during procurement export", e);
            throw new ApplicationException("Failed to generate procurement report", e);
        }
    }

    @GetMapping("/item-demand")
    @Operation(summary = "Get Item Demand Prediction",
            description = "Forecasts units needed based on sales in [from, to]; trend is versus the equally-sized prior window.")
    public ResponseEntity<List<ItemDemandPredictionDto>> getItemDemandPrediction(
            @RequestParam(required = false) Long itemId,
            @Parameter(description = "Range start (inclusive). Defaults to 30 days before today.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Range end (inclusive). Defaults to today.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        AnalyticsRange range = defaultLast30Days(from, to);
        logger.debug("Fetching demand prediction for itemId={} in {}..{}", itemId, range.getFrom(), range.getTo());
        return ResponseEntity.ok(analyticsService.predictItemDemand(itemId, range));
    }

    @GetMapping("/top-items")
    @Operation(summary = "Top rising/falling items",
            description = "Returns top rising and falling items comparing [from, to] against the equally-sized prior window.")
    public ResponseEntity<List<TopItemDto>> getTopRisingFallingItems(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.getTopRisingFallingItems(defaultLast30Days(from, to)));
    }

    @GetMapping("/seasonal-trends")
    @Operation(summary = "Seasonal trends",
            description = "Returns seasonal sales trends over [from, to]. Defaults to the last 12 months if unspecified.")
    public ResponseEntity<List<SeasonalTrendDto>> getSeasonalTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.getSeasonalTrends(defaultLast12Months(from, to)));
    }

    @GetMapping("/future-purchase-orders")
    @Operation(summary = "Get Purchase Suggestions", description = "Returns items below threshold with suggested restock quantities.")
    public ResponseEntity<List<PurchaseOrderSuggestionDto>> getFuturePurchaseOrderSuggestions() {
        return ResponseEntity.ok(analyticsService.suggestFuturePurchaseOrders());
    }

    @GetMapping("/churn-prediction")
    @Operation(summary = "Get Churn Prediction",
            description = "Identifies customers with no purchase in the last N days (default 90).")
    public ResponseEntity<List<ChurnPredictionDto>> getChurnPrediction(
            @Parameter(description = "Days of inactivity that mark a customer as at-risk.")
            @RequestParam(defaultValue = "90") int thresholdDays
    ) {
        return ResponseEntity.ok(analyticsService.predictChurn(thresholdDays));
    }

    @GetMapping("/revenue-leakage")
    @Operation(summary = "Get Financial Analytics", description = "Aggregates revenue at risk and total investment needed.")
    public ResponseEntity<Map<String, BigDecimal>> getRevenueLeakage(
            @RequestParam(defaultValue = "90") int thresholdDays
    ) {
        List<ChurnPredictionDto> churns = analyticsService.predictChurn(thresholdDays);
        List<PurchaseOrderSuggestionDto> purchases = analyticsService.suggestFuturePurchaseOrders();

        BigDecimal totalLost = churns.stream()
                .map(ChurnPredictionDto::getRevenueAtRisk)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInvestment = purchases.stream()
                .map(PurchaseOrderSuggestionDto::getEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResponseEntity.ok(Map.of(
                "lostOpportunityRevenue", totalLost,
                "investmentNeeded", totalInvestment
        ));
    }

    @GetMapping("/customer-trends")
    @Operation(summary = "Get Customer Trends",
            description = "Analyzes buying patterns and frequently bought items in [from, to].")
    public ResponseEntity<List<CustomerTrendDto>> getCustomerTrends(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.getCustomerTrends(customerId, defaultLast30Days(from, to)));
    }

    @GetMapping("/kpis")
    @Operation(summary = "KPI summary",
            description = "Total revenue, sale count, average order value and unique customers for [from, to], each paired with the equally-sized prior period.")
    public ResponseEntity<KpiSummaryDto> getKpis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.getKpiSummary(defaultLast30Days(from, to)));
    }

    @GetMapping("/revenue-timeseries")
    @Operation(summary = "Revenue time series",
            description = "Bucketed revenue and sale count. Granularity = DAY (default), WEEK or MONTH. Buckets are dense — empty periods return zeros so the chart has no gaps.")
    public ResponseEntity<RevenueTimeSeriesDto> getRevenueTimeSeries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "DAY") String granularity
    ) {
        AnalyticsRange base = defaultLast30Days(from, to);
        AnalyticsRange range = AnalyticsRange.of(
                base.getFrom(), base.getTo(), AnalyticsRange.Granularity.fromString(granularity));
        return ResponseEntity.ok(analyticsService.getRevenueTimeSeries(range));
    }

    @GetMapping("/gross-margin")
    @Operation(summary = "Gross margin",
            description = "Revenue, COGS and gross-margin percentage over [from, to]. COGS uses the most recent purchase-invoice unit cost per variant.")
    public ResponseEntity<GrossMarginDto> getGrossMargin(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.getGrossMargin(defaultLast30Days(from, to)));
    }

    @GetMapping("/payment-mix")
    @Operation(summary = "Payment method mix",
            description = "Share of sale-side payments by method (Cash/UPI/Card/etc.) with amount, transaction count and percentage.")
    public ResponseEntity<PaymentMixDto> getPaymentMix(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.getPaymentMix(defaultLast30Days(from, to)));
    }

    private AnalyticsRange defaultLast30Days(LocalDate from, LocalDate to) {
        return (from == null && to == null) ? AnalyticsRange.last30Days() : AnalyticsRange.of(from, to);
    }

    private AnalyticsRange defaultLast12Months(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            LocalDate today = LocalDate.now();
            return AnalyticsRange.of(today.minusMonths(12), today);
        }
        return AnalyticsRange.of(from, to);
    }

    /**
     * Helper to map file formats to MediaTypes for browser compatibility.
     */
    private String getContentType(String format) {
        return switch (format.toLowerCase()) {
            case "xlsx", "excel" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pdf" -> "application/pdf";
            case "csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }
}