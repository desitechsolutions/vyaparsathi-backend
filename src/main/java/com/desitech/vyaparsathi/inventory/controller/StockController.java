
package com.desitech.vyaparsathi.inventory.controller;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.inventory.export.StockExportService;

import com.desitech.vyaparsathi.inventory.dto.*;
import com.desitech.vyaparsathi.inventory.service.StockImportService;
import com.desitech.vyaparsathi.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    @Autowired
    private StockExportService stockExportService;
    @Autowired
    private StockImportService stockImportService;

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

    @GetMapping("/batch-wise")
    @Operation(summary = "Get batch-wise stock breakdown",
               description = "Returns one entry per (item variant, batch, expiry date) combination so that " +
                       "shops tracking perishables can see each batch's remaining quantity and expiry date separately. " +
                       "Only batches with a positive remaining quantity are included.")
    @ApiResponse(responseCode = "200", description = "Batch-wise stock retrieved successfully")
    public ResponseEntity<List<BatchStockDto>> getBatchWiseStock() {
        try {
            List<BatchStockDto> result = service.getBatchWiseStock();
            logger.info("Fetched batch-wise stock levels");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching batch-wise stock: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to fetch batch-wise stock", e);
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

    @GetMapping("/expiry-alerts")
    @Operation(summary = "Get item expiry alerts",
               description = "Returns items expiring within the specified number of days. " +
                       "Used by shops tracking perishables (FMCG, food, cosmetics) to identify near-expiry or expired stock. " +
                       "Default window is 90 days.")
    @ApiResponse(responseCode = "200", description = "Expiry alerts retrieved successfully")
    public ResponseEntity<List<ExpiryAlertDto>> getExpiryAlerts(
            @Parameter(description = "Number of days ahead to check for expiry (default 90)")
            @RequestParam(defaultValue = "90") int daysBeforeExpiry) {
        try {
            List<ExpiryAlertDto> result = service.getExpiryAlerts(daysBeforeExpiry);
            logger.info("Fetched expiry alerts for next {} days", daysBeforeExpiry);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching expiry alerts: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to fetch expiry alerts", e);
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

    @GetMapping("/export")
    @Operation(summary = "Export stock data",
            description = "Exports current inventory if dates are null, otherwise exports movement history.")
    public ResponseEntity<byte[]> exportStock(
            @Parameter(description = "Start date (optional)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (optional)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Format: excel, csv, pdf")
            @RequestParam(defaultValue = "excel") String format) {
        try {
            byte[] file;
            String fileName;

            if (startDate != null && endDate != null) {
                // Scenario: Movement History Report
                List<StockMovementDto> data = service.getStockMovements(startDate, endDate);
                file = stockExportService.exportStockMovements(data, format);
                fileName = "Stock_Movements_" + startDate.toLocalDate() + "_to_" + endDate.toLocalDate();
            } else {
                // Scenario: Current Inventory Summary
                List<CurrentStockDto> data = service.getCurrentStock();
                file = stockExportService.exportCurrentStock(data, format);
                fileName = "Current_Stock_Summary_" + java.time.LocalDate.now();
            }

            String extension = "excel".equalsIgnoreCase(format) ? "xlsx" : format.toLowerCase();
            MediaType mediaType = getMediaType(format);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName + "." + extension)
                    .contentType(mediaType)
                    .body(file);

        } catch (ExportAppException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Export failed", e);
            throw new ApplicationException("Failed to generate export file", e);
        }
    }

    private MediaType getMediaType(String format) {
        if ("excel".equalsIgnoreCase(format)) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        if ("pdf".equalsIgnoreCase(format)) return MediaType.APPLICATION_PDF;
        return MediaType.parseMediaType("text/csv");
    }

    // -------------------------------------------------------------------------
    // Excel Import endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/import/template")
    @Operation(summary = "Download stock import template",
               description = "Returns a blank Excel workbook with the correct column headers and a sample row " +
                       "for bulk-import of existing inventory.")
    @ApiResponse(responseCode = "200", description = "Template downloaded successfully")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        try {
            byte[] template = stockImportService.generateImportTemplate();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=stock_import_template.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(template);
        } catch (Exception e) {
            logger.error("Failed to generate import template: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to generate import template", e);
        }
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import stock from Excel",
               description = "Accepts an .xlsx file in the import template format. " +
                       "For each row the service finds or creates the Item and ItemVariant (matched by SKU), " +
                       "then records an ADD stock movement. " +
                       "Returns a summary with success/error counts and per-row error messages.")
    @ApiResponse(responseCode = "200", description = "Import processed – check result for row-level errors")
    public ResponseEntity<StockImportResultDto> importStock(
            @Parameter(description = "Excel file (.xlsx) in the import template format")
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApplicationException("Uploaded file is empty", null);
        }
        try {
            StockImportResultDto result = stockImportService.importFromExcel(file);
            logger.info("Stock import completed – success={}, errors={}", result.getSuccessCount(), result.getErrorCount());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Stock import failed: {}", e.getMessage(), e);
            throw new ApplicationException("Stock import failed: " + e.getMessage(), e);
        }
    }

    // ── V94 enterprise endpoints ────────────────────────────────────────

    @org.springframework.beans.factory.annotation.Autowired
    private com.desitech.vyaparsathi.inventory.service.InventoryReportService inventoryReportService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.desitech.vyaparsathi.inventory.service.InventoryReservationService reservationService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.desitech.vyaparsathi.inventory.service.StockAdjustmentApprovalService adjustmentApprovalService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.desitech.vyaparsathi.inventory.service.CycleCountService cycleCountService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.desitech.vyaparsathi.inventory.service.BatchRecallService batchRecallService;

    // Reports pack.
    @GetMapping("/reports/valuation")
    public ResponseEntity<java.util.Map<String, Object>> valuation() {
        return ResponseEntity.ok(inventoryReportService.valuation());
    }

    @GetMapping("/reports/valuation.xlsx")
    public ResponseEntity<byte[]> valuationXlsx() {
        byte[] body = inventoryReportService.valuationXlsx();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"stock-valuation.xlsx\"");
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/reports/dead-stock")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> deadStock(
            @RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(inventoryReportService.deadStock(days));
    }

    @GetMapping("/reports/shrinkage")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> shrinkage(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        java.time.LocalDate f = from != null ? java.time.LocalDate.parse(from) : null;
        java.time.LocalDate t = to != null ? java.time.LocalDate.parse(to) : null;
        return ResponseEntity.ok(inventoryReportService.shrinkage(f, t));
    }

    @GetMapping("/reports/ageing")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> ageing() {
        return ResponseEntity.ok(inventoryReportService.ageing());
    }

    @GetMapping("/reports/turnover")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> turnover(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(inventoryReportService.turnover(days));
    }

    // Reservations.
    @PostMapping("/reservations")
    public ResponseEntity<com.desitech.vyaparsathi.inventory.entity.InventoryReservation> reserve(
            @RequestBody java.util.Map<String, Object> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        Long variantId = Long.valueOf(body.get("itemVariantId").toString());
        java.math.BigDecimal qty = new java.math.BigDecimal(body.get("quantity").toString());
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        String refType = body.get("referenceType") != null ? body.get("referenceType").toString() : null;
        Long refId = body.get("referenceId") != null ? Long.valueOf(body.get("referenceId").toString()) : null;
        java.time.LocalDateTime expiresAt = body.get("expiresAt") != null
                ? java.time.LocalDateTime.parse(body.get("expiresAt").toString()) : null;
        String user = principal != null ? principal.getUsername() : "system";
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(reservationService.reserve(variantId, qty, reason, refType, refId, user, expiresAt));
    }

    @PostMapping("/reservations/{id}/consume")
    public ResponseEntity<com.desitech.vyaparsathi.inventory.entity.InventoryReservation> consume(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.consume(id));
    }

    @PostMapping("/reservations/{id}/release")
    public ResponseEntity<com.desitech.vyaparsathi.inventory.entity.InventoryReservation> release(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.release(id));
    }

    // Adjustment approvals.
    @GetMapping("/adjustment-approvals")
    public ResponseEntity<java.util.List<com.desitech.vyaparsathi.inventory.entity.StockAdjustmentApproval>> pendingAdjustments() {
        return ResponseEntity.ok(adjustmentApprovalService.pending());
    }

    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PostMapping("/adjustment-approvals/{id}/approve")
    public ResponseEntity<com.desitech.vyaparsathi.inventory.entity.StockAdjustmentApproval> approveAdjustment(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        Long userId = principal != null ? principal.getId() : null;
        String note = body != null ? body.get("note") : null;
        return ResponseEntity.ok(adjustmentApprovalService.approve(id, userId, note));
    }

    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PostMapping("/adjustment-approvals/{id}/reject")
    public ResponseEntity<com.desitech.vyaparsathi.inventory.entity.StockAdjustmentApproval> rejectAdjustment(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        Long userId = principal != null ? principal.getId() : null;
        String note = body != null ? body.get("note") : null;
        return ResponseEntity.ok(adjustmentApprovalService.reject(id, userId, note));
    }

    // Cycle count.
    @PostMapping("/cycle-counts")
    public ResponseEntity<com.desitech.vyaparsathi.inventory.entity.CycleCount> planCycleCount(
            @RequestBody java.util.Map<String, String> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        String initiator = principal != null ? principal.getUsername() : "system";
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(cycleCountService.plan(body.getOrDefault("scope", "FULL"), initiator, body.get("notes")));
    }

    @GetMapping("/cycle-counts")
    public ResponseEntity<java.util.List<com.desitech.vyaparsathi.inventory.entity.CycleCount>> listCycleCounts() {
        return ResponseEntity.ok(cycleCountService.listAll());
    }

    @GetMapping("/cycle-counts/{id}")
    public ResponseEntity<com.desitech.vyaparsathi.inventory.entity.CycleCount> getCycleCount(@PathVariable Long id) {
        return ResponseEntity.ok(cycleCountService.get(id));
    }

    @PostMapping("/cycle-counts/{id}/lines/{lineId}/count")
    public ResponseEntity<com.desitech.vyaparsathi.inventory.entity.CycleCount> recordCount(
            @PathVariable Long id, @PathVariable Long lineId,
            @RequestBody java.util.Map<String, Object> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        java.math.BigDecimal qty = new java.math.BigDecimal(body.get("countedQty").toString());
        String counter = principal != null ? principal.getUsername() : "system";
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        return ResponseEntity.ok(cycleCountService.recordCount(id, lineId, qty, counter, reason));
    }

    @PostMapping("/cycle-counts/{id}/commit")
    public ResponseEntity<com.desitech.vyaparsathi.inventory.entity.CycleCount> commitCycleCount(@PathVariable Long id) {
        return ResponseEntity.ok(cycleCountService.commit(id));
    }

    @PostMapping("/cycle-counts/{id}/cancel")
    public ResponseEntity<com.desitech.vyaparsathi.inventory.entity.CycleCount> cancelCycleCount(
            @PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(cycleCountService.cancel(id, reason));
    }

    // Batch recall.
    @PostMapping("/recalls")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<com.desitech.vyaparsathi.inventory.entity.BatchRecall> openRecall(
            @RequestBody java.util.Map<String, Object> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        String batchNo = body.get("batchNumber").toString();
        Long variantId = body.get("itemVariantId") != null ? Long.valueOf(body.get("itemVariantId").toString()) : null;
        String reason = body.get("reason") != null ? body.get("reason").toString() : "-";
        boolean notifySupplier = Boolean.parseBoolean(String.valueOf(body.getOrDefault("notifySupplier", false)));
        boolean notifyCustomers = Boolean.parseBoolean(String.valueOf(body.getOrDefault("notifyCustomers", false)));
        String initiator = principal != null ? principal.getUsername() : "system";
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(batchRecallService.open(batchNo, variantId, reason, initiator, notifySupplier, notifyCustomers));
    }

    @GetMapping("/recalls")
    public ResponseEntity<java.util.List<com.desitech.vyaparsathi.inventory.entity.BatchRecall>> listRecalls() {
        return ResponseEntity.ok(batchRecallService.listAll());
    }

    @PostMapping("/recalls/{id}/close")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<com.desitech.vyaparsathi.inventory.entity.BatchRecall> closeRecall(
            @PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
        return ResponseEntity.ok(batchRecallService.close(id, body != null ? body.get("note") : null));
    }

    @PostMapping("/recalls/{id}/impacts/{impactId}/outcome")
    public ResponseEntity<com.desitech.vyaparsathi.inventory.entity.BatchRecall> updateImpactOutcome(
            @PathVariable Long id, @PathVariable Long impactId,
            @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(batchRecallService.updateImpactOutcome(id, impactId, body.get("outcome")));
    }
}