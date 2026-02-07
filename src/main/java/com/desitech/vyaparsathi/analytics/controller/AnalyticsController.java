package com.desitech.vyaparsathi.analytics.controller;

import com.desitech.vyaparsathi.analytics.dto.*;
import com.desitech.vyaparsathi.analytics.service.AnalyticsExportService;
import com.desitech.vyaparsathi.analytics.service.AnalyticsService;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    @Operation(summary = "Get Item Demand Prediction", description = "Forecasts units needed for the next 30 days.")
    public ResponseEntity<List<ItemDemandPredictionDto>> getItemDemandPrediction(
            @RequestParam(required = false) Long itemId
    ) {
        logger.debug("Fetching demand prediction for itemId: {}", itemId);
        return ResponseEntity.ok(analyticsService.predictItemDemand(itemId));
    }

    @GetMapping("/top-items")
    @Operation(summary = "Top rising/falling items", description = "Returns top rising and falling items based on sales trends.")
    public ResponseEntity<List<TopItemDto>> getTopRisingFallingItems() {
        return ResponseEntity.ok(analyticsService.getTopRisingFallingItems());
    }
    @GetMapping("/seasonal-trends")
    @Operation(summary = "Seasonal trends", description = "Returns seasonal sales trends.")
    public ResponseEntity<List<SeasonalTrendDto>> getSeasonalTrends() {
        return ResponseEntity.ok(analyticsService.getSeasonalTrends());
    }
    @GetMapping("/future-purchase-orders")
    @Operation(summary = "Get Purchase Suggestions", description = "Returns items below threshold with suggested restock quantities.")
    public ResponseEntity<List<PurchaseOrderSuggestionDto>> getFuturePurchaseOrderSuggestions() {
        return ResponseEntity.ok(analyticsService.suggestFuturePurchaseOrders());
    }

    @GetMapping("/churn-prediction")
    @Operation(summary = "Get Churn Prediction", description = "Identifies customers at risk and the revenue they represent.")
    public ResponseEntity<List<ChurnPredictionDto>> getChurnPrediction() {
        return ResponseEntity.ok(analyticsService.predictChurn());
    }

    @GetMapping("/revenue-leakage")
    @Operation(summary = "Get Financial Analytics", description = "Aggregates revenue at risk and total investment needed.")
    public ResponseEntity<Map<String, BigDecimal>> getRevenueLeakage() {
        List<ChurnPredictionDto> churns = analyticsService.predictChurn();
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
    @Operation(summary = "Get Customer Trends", description = "Analyzes buying patterns and frequently bought items.")
    public ResponseEntity<List<CustomerTrendDto>> getCustomerTrends(
            @RequestParam(required = false) Long customerId
    ) {
        return ResponseEntity.ok(analyticsService.getCustomerTrends(customerId));
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