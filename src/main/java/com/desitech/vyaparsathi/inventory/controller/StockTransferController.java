package com.desitech.vyaparsathi.inventory.controller;

import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.inventory.dto.StockTransferCreateDto;
import com.desitech.vyaparsathi.inventory.dto.StockTransferDto;
import com.desitech.vyaparsathi.inventory.service.StockTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for multi-location stock transfers.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST   /api/stock-transfers            – create a new PENDING transfer</li>
 *   <li>GET    /api/stock-transfers             – list all transfers for the current shop</li>
 *   <li>GET    /api/stock-transfers/{id}        – get a single transfer by ID</li>
 *   <li>POST   /api/stock-transfers/{id}/execute – execute a PENDING transfer</li>
 *   <li>POST   /api/stock-transfers/{id}/cancel  – cancel a PENDING transfer</li>
 *   <li>GET    /api/stock-transfers/pending-count – pending transfer count (dashboard badge)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/stock-transfers")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'ADMIN')")
@Tag(name = "Stock Transfers", description = "Multi-location stock transfer management")
public class StockTransferController {

    private static final Logger log = LoggerFactory.getLogger(StockTransferController.class);

    @Autowired
    private StockTransferService service;

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @PostMapping
    @Operation(summary = "Create a new stock transfer",
               description = "Creates a PENDING stock transfer. No stock is moved until you call /execute.")
    public ResponseEntity<StockTransferDto> create(@RequestBody StockTransferCreateDto dto) {
        try {
            StockTransferDto result = service.createTransfer(dto);
            log.info("Created transfer {}", result.getTransferNumber());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to create stock transfer: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to create stock transfer: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // List & Get
    // -------------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "List all stock transfers",
               description = "Returns all transfers where the current shop is either the sender or the receiver, ordered by date descending.")
    public ResponseEntity<List<StockTransferDto>> list() {
        try {
            List<StockTransferDto> result = service.getTransfersForCurrentShop();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to list stock transfers: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to list stock transfers", e);
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a stock transfer by ID")
    public ResponseEntity<StockTransferDto> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.getTransfer(id));
        } catch (Exception e) {
            log.error("Failed to fetch transfer id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Stock transfer not found: " + id, e);
        }
    }

    @GetMapping("/pending-count")
    @Operation(summary = "Get count of PENDING transfers",
               description = "Returns the number of PENDING transfers. Used for dashboard notification badges.")
    public ResponseEntity<Map<String, Long>> pendingCount() {
        return ResponseEntity.ok(Map.of("pendingCount", service.getPendingCount()));
    }

    // -------------------------------------------------------------------------
    // Execute
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/execute")
    @Operation(summary = "Execute a PENDING stock transfer",
               description = "Validates stock availability, deducts from the source shop, adds to the destination shop, and marks the transfer as COMPLETED.")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<StockTransferDto> execute(@PathVariable Long id) {
        try {
            StockTransferDto result = service.executeTransfer(id);
            log.info("Executed transfer id={} ({})", id, result.getTransferNumber());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to execute transfer id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to execute transfer: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Cancel
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a PENDING stock transfer",
               description = "Voids the transfer without moving any stock. Only PENDING transfers can be cancelled.")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<StockTransferDto> cancel(@PathVariable Long id) {
        try {
            StockTransferDto result = service.cancelTransfer(id);
            log.info("Cancelled transfer id={} ({})", id, result.getTransferNumber());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to cancel transfer id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to cancel transfer: " + e.getMessage(), e);
        }
    }

    // ── V94 approval + in-transit flow ──────────────────────────────────

    @PostMapping("/{id}/request-approval")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<StockTransferDto> requestApproval(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String note = body != null ? body.get("note") : null;
        return ResponseEntity.ok(service.requestApproval(id, note));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<StockTransferDto> approve(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        Long userId = principal != null ? principal.getId() : null;
        String note = body != null ? body.get("note") : null;
        return ResponseEntity.ok(service.approve(id, userId, note));
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<StockTransferDto> dispatch(@PathVariable Long id) {
        return ResponseEntity.ok(service.dispatch(id));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<StockTransferDto> receive(@PathVariable Long id) {
        return ResponseEntity.ok(service.confirmArrival(id));
    }
}
