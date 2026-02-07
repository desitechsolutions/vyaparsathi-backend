
package com.desitech.vyaparsathi.inventory.controller;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.inventory.export.StockExportService;

import com.desitech.vyaparsathi.inventory.dto.*;
import com.desitech.vyaparsathi.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/stock")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF','ADMIN')")
@Tag(name = "Stock Management", description = "Operations for inventory stock management including cost tracking, movement history, and stock adjustments")
public class StockController {

    private static final Logger logger = LoggerFactory.getLogger(StockController.class);

    @Autowired
    private StockService service;

    @PostMapping("/add")
    @Operation(summary = "Add stock manually",
            description = "Manually add stock with cost tracking. Records a new stock movement.")
    @ApiResponse(responseCode = "200", description = "Stock added successfully")
    public ResponseEntity<StockMovementDto> addStock(@RequestBody StockAddDto dto) { // Return StockMovementDto
        try {
            StockMovementDto result = service.addStockFromDto(dto);
            logger.info("Added stock for itemVariantId={}", dto.getItemVariantId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error adding stock for itemVariantId={}: {}", dto.getItemVariantId(), e.getMessage(), e);
            throw new ApplicationException("Failed to add stock", e);
        }
    }
    @GetMapping
    @Operation(summary = "Get current stock levels", 
               description = "Retrieve current stock levels for all item variants")
    @ApiResponse(responseCode = "200", description = "Current stock levels retrieved successfully")
    public ResponseEntity<List<CurrentStockDto>> getCurrentStock() {
        try {
            List<CurrentStockDto> result = service.getCurrentStock();
            logger.info("Fetched current stock levels");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching current stock levels: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to fetch current stock levels", e);
        }
    }

    @GetMapping("/movements/{itemVariantId}")
    @Operation(summary = "Get stock movement history for item", 
               description = "Retrieve complete stock movement history for a specific item variant")
    @ApiResponse(responseCode = "200", description = "Stock movements retrieved successfully")
    public ResponseEntity<List<StockMovementDto>> getStockMovements(
            @Parameter(description = "ID of the item variant") @PathVariable Long itemVariantId) {
        try {
            List<StockMovementDto> result = service.getStockMovements(itemVariantId);
            logger.info("Fetched stock movements for itemVariantId={}", itemVariantId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching stock movements for itemVariantId={}: {}", itemVariantId, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch stock movements", e);
        }
    }

    @GetMapping("/movements")
    @Operation(summary = "Get stock movements within date range", 
               description = "Retrieve all stock movements within specified date range for reporting purposes")
    @ApiResponse(responseCode = "200", description = "Stock movements retrieved successfully")
    public ResponseEntity<List<StockMovementDto>> getStockMovements(
            @Parameter(description = "Start date for the report") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date for the report") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        try {
            List<StockMovementDto> result = service.getStockMovements(startDate, endDate);
            logger.info("Fetched stock movements from {} to {}", startDate, endDate);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching stock movements from {} to {}: {}", startDate, endDate, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch stock movements", e);
        }
    }

    @GetMapping("/low-stock-alerts")
    @Operation(summary = "Get low stock alerts", 
               description = "Retrieve items that are below their configured stock thresholds")
    @ApiResponse(responseCode = "200", description = "Low stock alerts retrieved successfully")
    public ResponseEntity<List<LowStockAlertDto>> getLowStockAlerts() {
        try {
            List<LowStockAlertDto> result = service.getLowStockAlerts();
            logger.info("Fetched low stock alerts");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching low stock alerts: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to fetch low stock alerts", e);
        }
    }

    @PostMapping("/adjust")
    @Operation(summary = "Manual stock adjustment",
            description = "Perform manual stock adjustment. Records a new stock movement.")
    @ApiResponse(responseCode = "200", description = "Stock adjusted successfully")
    public ResponseEntity<StockMovementDto> adjustStock(@RequestBody StockAdjustmentDto dto) { // Return StockMovementDto
        try {
            StockMovementDto result = service.adjustStock(dto);
            logger.info("Adjusted stock for itemVariantId={}", dto.getItemVariantId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error adjusting stock for itemVariantId={}: {}", dto.getItemVariantId(), e.getMessage(), e);
            throw new ApplicationException("Failed to adjust stock", e);
        }
    }

    @Autowired
    private StockExportService stockExportService;
    @GetMapping("/movements/export")
    @Operation(summary = "Export stock movements within date range",
            description = "Export all stock movements within specified date range as CSV, Excel, or PDF for reporting purposes")
    @ApiResponse(responseCode = "200", description = "Stock movements exported successfully")
    public ResponseEntity<byte[]> exportStockMovements(
            @Parameter(description = "Start date for the report") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date for the report") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Export format: csv, excel, pdf") @RequestParam(defaultValue = "csv") String format) {
        try {
            List<StockMovementDto> data = service.getStockMovements(startDate, endDate);
            byte[] file = stockExportService.exportStockMovements(data, format);
            String contentType = "csv".equalsIgnoreCase(format) ? "text/csv" :
                    ("excel".equalsIgnoreCase(format) ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" :
                            ("pdf".equalsIgnoreCase(format) ? "application/pdf" : "application/octet-stream"));
            String fileName = "stock-movements-" + startDate.toLocalDate() + "-" + endDate.toLocalDate() + "." + ("csv".equalsIgnoreCase(format) ? "csv" : ("excel".equalsIgnoreCase(format) ? "xlsx" : ("pdf".equalsIgnoreCase(format) ? "pdf" : "dat")));
            logger.info("Exported stock movements as {} ({} bytes)", format, file.length);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + fileName)
                    .header("Content-Type", contentType)
                    .body(file);
        } catch (ExportAppException e) {
            logger.error("Failed to export stock movements: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during stock movement export", e);
            throw new ApplicationException("Failed to export stock movements", e);
        }
    }

}